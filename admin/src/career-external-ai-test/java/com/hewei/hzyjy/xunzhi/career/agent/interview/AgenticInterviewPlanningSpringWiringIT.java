package com.hewei.hzyjy.xunzhi.career.agent.interview;

import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.ai.LangChain4jAgentAdapter;
import com.hewei.hzyjy.xunzhi.career.config.AgenticInterviewRuntimeConfiguration;
import com.hewei.hzyjy.xunzhi.career.memory.DecisionIndex;
import com.hewei.hzyjy.xunzhi.career.memory.HybridCompactingChatMemory;
import com.hewei.hzyjy.xunzhi.career.memory.InterviewRuleBasedScorer;
import com.hewei.hzyjy.xunzhi.career.memory.LangChain4jHybridMemoryAdapter;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    void interviewAgenticBeansAreDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("AgenticJDAlignmentAgent");
            assertThat(context).doesNotHaveBean("AgenticInterviewCoordinatorAgent");
            assertThat(context).doesNotHaveBean("AgenticInterviewReflectorAgent");
            assertThat(context).doesNotHaveBean("AgenticInterviewOrchestratorService");
            assertThat(context).doesNotHaveBean("AgenticJavaTechInterviewerAgent");
        });
    }

    @Test
    void interviewAgenticBeansCanBeEnabledExplicitly() {
        contextRunner
                .withPropertyValues("xunzhi-agent.career.interview.agentic.enabled=true")
                .run(context -> {
                    assertThat(context).hasBean("AgenticJDAlignmentAgent");
                    assertThat(context).hasBean("AgenticInterviewCoordinatorAgent");
                    assertThat(context).hasBean("AgenticInterviewReflectorAgent");
                    assertThat(context).hasBean("AgenticInterviewOrchestratorService");
                    assertThat(context).hasBean("AgenticJavaTechInterviewerAgent");

                    LangChain4jAgentAdapter adapter = new LangChain4jAgentAdapter(context, mock(org.springframework.beans.factory.ObjectProvider.class));
                    JdAlignmentResult directAlignment = adapter.invoke("JDAlignmentAgent", "align", java.util.Map.of(
                            "memoryId", "interview:u1:s-agentic",
                            "cv", CvBO.builder().id(7L).summary("Java Redis backend").build(),
                            "jobDescription", "Java Redis backend engineer"
                    ), JdAlignmentResult.class);
                    TechnicalQuestionSuggestion directTechnicalQuestion = adapter.invoke("JavaTechInterviewerAgent", "generateQuestion", java.util.Map.of(
                            "memoryId", "interview:u1:s-agentic",
                            "cv", CvBO.builder().id(7L).summary("Java Redis backend").build(),
                            "jobDescription", "Java Redis backend engineer",
                            "alignment", directAlignment,
                            "stages", java.util.List.of(InterviewStagePlan.builder()
                                    .stageName("AGENTIC_JD_ALIGNMENT")
                                    .goal("Validate JD fit")
                                    .questionSeeds(java.util.List.of("Java", "Redis"))
                                    .build()),
                            "skillContext", "question probing skill"
                    ), TechnicalQuestionSuggestion.class);
                    ReflectionResult directReflection = adapter.invoke("InterviewReflectorAgent", "reflect", java.util.Map.of(
                            "memoryId", "interview:u1:s-agentic",
                            "currentQuestion", "How did you use Redis?",
                            "userAnswer", "I used cache aside but need to add metrics.",
                            "cv", CvBO.builder().id(7L).summary("Java Redis backend").build(),
                            "memoryView", "previous decisions"
                    ), ReflectionResult.class);

                    assertThat(directAlignment.summary()).contains("Agentic JD alignment");
                    assertThat(directTechnicalQuestion.question()).contains("Agentic JavaTech question");
                    assertThat(directReflection.feedback()).contains("Agentic reflection");
                    assertThat(hybridMemory.messages("interview:u1:s-agentic"))
                            .extracting(message -> message.content())
                            .anyMatch(content -> content.contains("Java Redis backend"));
                });
    }
}
