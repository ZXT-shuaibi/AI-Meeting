package com.hewei.hzyjy.xunzhi.career.agent.interview;

import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.ai.LangChain4jAgentAdapter;
import com.hewei.hzyjy.xunzhi.career.config.AgenticInterviewRuntimeConfiguration;
import com.hewei.hzyjy.xunzhi.career.memory.DecisionIndex;
import com.hewei.hzyjy.xunzhi.career.memory.HybridCompactingChatMemory;
import com.hewei.hzyjy.xunzhi.career.memory.InterviewRuleBasedScorer;
import com.hewei.hzyjy.xunzhi.career.memory.LangChain4jHybridMemoryAdapter;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.skill.ClasspathCareerSkillRegistry;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgenticInterviewPlanningSpringWiringIT {

    private final HybridCompactingChatMemory hybridMemory = new HybridCompactingChatMemory(
            request -> AiGatewayResult.builder().content("compressed").build(),
            new InterviewRuleBasedScorer(),
            new DecisionIndex()
    );

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AgenticInterviewRuntimeConfiguration.class)
            .withBean(ChatModel.class, ScriptedInterviewChatModel::new)
            .withBean(HybridCompactingChatMemory.class, () -> hybridMemory)
            .withBean(LangChain4jHybridMemoryAdapter.class, () -> new LangChain4jHybridMemoryAdapter(hybridMemory));

    @BeforeEach
    @AfterEach
    void resetAgenticMemoryProvider() {
        AgenticJdAlignmentAgent.resetChatMemoryProvider();
    }

    @Test
    void interviewPlanningServiceUsesRealAgenticBeansThroughGateway() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("AgenticJDAlignmentAgent");
            assertThat(context).hasBean("AgenticInterviewCoordinatorAgent");
            assertThat(context).hasBean("AgenticInterviewReflectorAgent");
            assertThat(context).hasBean("AgenticInterviewOrchestratorService");
            assertThat(context).hasBean("AgenticJavaTechInterviewerAgent");
            LangChain4jAgentAdapter adapter = new LangChain4jAgentAdapter(context, mock(ObjectProvider.class));
            JdAlignmentResult directAlignment = adapter.invoke("JDAlignmentAgent", "align", java.util.Map.of(
                    "memoryId", "interview:u1:s-agentic",
                    "cv", CvBO.builder().id(7L).summary("Java Redis backend").build(),
                    "jobDescription", "Java Redis backend engineer"
            ), JdAlignmentResult.class);
            AgenticInterviewStagePlanResult directStageResult = adapter.invoke("InterviewCoordinatorAgent", "coordinate", java.util.Map.of(
                    "memoryId", "interview:u1:s-agentic",
                    "alignment", directAlignment
            ), AgenticInterviewStagePlanResult.class);
            java.util.List<InterviewStagePlan> directStages = directStageResult.stages();
            InterviewPlan directPlan = adapter.invoke("InterviewOrchestratorService", "plan", java.util.Map.of(
                    "sessionId", "s-agentic",
                    "cv", CvBO.builder().id(7L).summary("Java Redis backend").build(),
                    "jobDescription", "Java Redis backend engineer",
                    "alignment", directAlignment,
                    "stages", directStages,
                    "firstQuestion", "candidate first question"
            ), InterviewPlan.class);
            TechnicalQuestionSuggestion directTechnicalQuestion = adapter.invoke("JavaTechInterviewerAgent", "generateQuestion", java.util.Map.of(
                    "memoryId", "interview:u1:s-agentic",
                    "cv", CvBO.builder().id(7L).summary("Java Redis backend").build(),
                    "jobDescription", "Java Redis backend engineer",
                    "alignment", directAlignment,
                    "stages", directStages,
                    "skillContext", "question probing skill"
            ), TechnicalQuestionSuggestion.class);
            ReflectionResult directReflection = adapter.invoke("InterviewReflectorAgent", "reflect", java.util.Map.of(
                    "memoryId", "interview:u1:s-agentic",
                    "currentQuestion", "How did you use Redis?",
                    "userAnswer", "I used cache aside but need to add metrics.",
                    "cv", CvBO.builder().id(7L).summary("Java Redis backend").build(),
                    "memoryView", "previous decisions"
            ), ReflectionResult.class);
            InterviewPlanningService service = new InterviewPlanningService(
                    request -> AiGatewayResult.builder().content("{}").build(),
                    hybridMemory,
                    provider(adapter),
                    provider(ClasspathCareerSkillRegistry.withBuiltIns())
            );

            InterviewPlan plan = service.plan(
                    "interview:u1:s-agentic",
                    "s-agentic",
                    CvBO.builder().id(7L).summary("Java Redis backend").build(),
                    "Java Redis backend engineer"
            );
            ReflectionResult reflection = service.reflect(
                    "interview:u1:s-agentic",
                    "How did you use Redis?",
                    "I used cache aside but need to add metrics.",
                    CvBO.builder().id(7L).summary("Java Redis backend").build()
            );

            assertThat(directAlignment.summary()).contains("Agentic JD alignment");
            assertThat(directStages).extracting(InterviewStagePlan::stageName).contains("AGENTIC_JD_ALIGNMENT");
            assertThat(directPlan.firstQuestion()).contains("Agentic first question");
            assertThat(directTechnicalQuestion.question()).contains("Agentic JavaTech question");
            assertThat(directReflection.feedback()).contains("Agentic reflection");
            assertThat(plan.firstQuestion()).contains("Agentic JavaTech question");
            assertThat(plan.alignment().summary()).contains("Agentic JD alignment");
            assertThat(plan.stages()).extracting(InterviewStagePlan::stageName).contains("AGENTIC_JD_ALIGNMENT");
            assertThat(reflection.decision()).isEqualTo(ReflectionDecision.PROBE);
            assertThat(reflection.feedback()).contains("Agentic reflection");
            assertThat(hybridMemory.view("interview:u1:s-agentic", 8).decisionContext()).contains("Plan-Execute-Reflect");
        });
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }
}
