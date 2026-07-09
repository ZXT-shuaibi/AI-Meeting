package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.config.AgenticCvRuntimeConfiguration;
import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.memory.DecisionIndex;
import com.hewei.hzyjy.xunzhi.career.memory.HybridCompactingChatMemory;
import com.hewei.hzyjy.xunzhi.career.memory.InterviewRuleBasedScorer;
import com.hewei.hzyjy.xunzhi.career.memory.LangChain4jHybridMemoryAdapter;
import com.hewei.hzyjy.xunzhi.career.observability.AiInvocationCompletedEvent;
import com.hewei.hzyjy.xunzhi.career.observability.AiInvocationStartedEvent;
import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.SystemMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgenticCvOptimizationSpringWiringIT {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AgenticCvRuntimeConfiguration.class)
            .withBean(ChatModel.class, ScriptedCvChatModel::new)
            .withBean(CvReviewer.class, () -> (cv, jd, templates) -> new CvReview(0.1, "fallback reviewer should not be used"))
            .withBean(ScoredCvTailor.class, () -> (cv, review, templates) -> cv.toBuilder().summary("fallback tailor should not be used").build());

    @BeforeEach
    @AfterEach
    void resetAgenticMemoryProvider() {
        AgenticCvOptimizationAgent.resetChatMemoryProvider();
    }

    @Test
    void primaryOrchestratorUsesAgenticRuntimeWhenExternalProfileBeansAreActive() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AgenticCvOptimizationRuntime.class);
            assertThat(context).hasSingleBean(AgenticCvOptimizationOrchestrator.class);

            CvOptimizationOrchestrator orchestrator = context.getBean(CvOptimizationOrchestrator.class);
            CvOptimizationResult result = orchestrator.optimize(
                    CvBO.builder().id(88L).name("candidate").summary("Java backend Redis project").build(),
                    "Java Redis backend engineer",
                    List.of("template"),
                    3
            );

            assertThat(orchestrator).isInstanceOf(AgenticCvOptimizationOrchestrator.class);
            assertThat(result.scoreGatePassed()).isTrue();
            assertThat(result.cv().getSummary()).contains("Optimized by Agentic tailor");
        });
    }

    @Test
    void agenticLoopChatMemoryUsesHybridCompactingMemoryWhenAdapterIsPresent() {
        HybridCompactingChatMemory hybridMemory = new HybridCompactingChatMemory(
                request -> AiGatewayResult.builder().content("compressed").build(),
                new InterviewRuleBasedScorer(),
                new DecisionIndex()
        );
        String memoryId = "resume:agentic:hybrid";

        contextRunner
                .withBean(HybridCompactingChatMemory.class, () -> hybridMemory)
                .withBean(LangChain4jHybridMemoryAdapter.class, () -> new LangChain4jHybridMemoryAdapter(hybridMemory))
                .run(context -> {
                    assertThat(context).hasSingleBean(LangChain4jHybridMemoryAdapter.class);

                    ChatMemory chatMemory = AgenticCvOptimizationAgent.chatMemory(memoryId);
                    chatMemory.add(UserMessage.from("candidate uploaded Java RAG resume"));

                    assertThat(hybridMemory.messages(memoryId))
                            .extracting(message -> message.content())
                            .contains("candidate uploaded Java RAG resume");
                });
    }

    @Test
    void agenticRuntimePublishesUnifiedTraceEvents() {
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
        contextRunner
                .withBean(AiTracePublisher.class, () -> new AiTracePublisher(eventPublisher))
                .run(context -> {
                    CvOptimizationOrchestrator orchestrator = context.getBean(CvOptimizationOrchestrator.class);

                    orchestrator.optimize(
                            CvBO.builder().id(89L).name("candidate").summary("Java backend Redis project").build(),
                            "Java Redis backend engineer",
                            List.of("template"),
                            3
                    );

                    assertThat(eventPublisher.events())
                            .anyMatch(AiInvocationStartedEvent.class::isInstance)
                            .anyMatch(AiInvocationCompletedEvent.class::isInstance)
                            .anyMatch(event -> event instanceof AiInvocationCompletedEvent completed
                                    && "langchain4j-agentic".equals(completed.provider())
                                    && "RESUME_TAILOR".equals(completed.sceneCode()));
                });
    }

    @Test
    void externalAiAgentPromptsStayAlignedWithMigratedCvSkills() {
        String reviewerPrompt = systemPrompt(AgenticCvReviewAgent.class, "review");
        String tailorPrompt = systemPrompt(AgenticScoredCvTailorAgent.class, "tailor");

        assertThat(reviewerPrompt)
                .contains("技术能力匹配度 (权重: 35%)")
                .contains("工作经验相关性 (权重: 30%)")
                .contains("项目经验价值 (权重: 25%)")
                .contains("教育背景与认证 (权重: 10%)")
                .contains("strengths/weaknesses/suggestions")
                .contains("参考模板");

        assertThat(tailorPrompt)
                .contains("真实性底线")
                .contains("禁止虚构")
                .contains("技能/经验/项目/教育")
                .contains("审核反馈")
                .contains("CvBO")
                .contains("meta.localeConfig.sectionLabels")
                .contains("yyyy-MM-dd");
    }

    private String systemPrompt(Class<?> agentType, String methodName) {
        return Arrays.stream(agentType.getMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .map(method -> method.getAnnotation(SystemMessage.class))
                .map(annotation -> String.join("\n", annotation.value()))
                .orElseThrow();
    }

    private static class RecordingEventPublisher implements ApplicationEventPublisher {
        private final List<Object> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }

        List<Object> events() {
            return events;
        }
    }
}
