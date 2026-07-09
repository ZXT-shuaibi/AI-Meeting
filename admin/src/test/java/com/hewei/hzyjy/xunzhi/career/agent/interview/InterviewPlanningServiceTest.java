package com.hewei.hzyjy.xunzhi.career.agent.interview;

import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import com.hewei.hzyjy.xunzhi.career.memory.DecisionIndex;
import com.hewei.hzyjy.xunzhi.career.memory.HybridCompactingChatMemory;
import com.hewei.hzyjy.xunzhi.career.memory.InterviewRuleBasedScorer;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.skill.CareerSkill;
import com.hewei.hzyjy.xunzhi.career.skill.CareerSkillRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewPlanningServiceTest {

    @Test
    void reflectionUsesSpringAiDecisionWhenResponseIsValid() {
        AiGateway aiGateway = request -> AiGatewayResult.builder()
                .content("{\"score\":8,\"decision\":\"STAGE_FINISH\",\"feedback\":\"Enough depth.\"}")
                .provider("test")
                .build();
        HybridCompactingChatMemory memory = new HybridCompactingChatMemory(aiGateway, new InterviewRuleBasedScorer(), new DecisionIndex());
        InterviewPlanningService service = new InterviewPlanningService(aiGateway, memory);

        ReflectionResult result = service.reflect("interview:s1", "How did you use Redis?", "I handled cache penetration with metrics and index tradeoff.", CvBO.builder().id(1L).summary("Java Redis").build());

        assertEquals(8, result.score());
        assertEquals(ReflectionDecision.STAGE_FINISH, result.decision());
        assertTrue(result.feedback().contains("Enough depth"));
    }

    @Test
    void reflectionFallsBackToLocalHeuristicDecisionWhenSpringAiResponseIsIncomplete() {
        AiGateway aiGateway = request -> AiGatewayResult.builder()
                .content("The answer is too shallow and needs concrete metrics and tradeoff details.")
                .provider("test")
                .build();
        HybridCompactingChatMemory memory = new HybridCompactingChatMemory(aiGateway, new InterviewRuleBasedScorer(), new DecisionIndex());
        InterviewPlanningService service = new InterviewPlanningService(aiGateway, memory);

        ReflectionResult result = service.reflect(
                "interview:s2",
                "How did you use Redis?",
                "I used cache aside.",
                CvBO.builder().id(2L).summary("Java Redis").build());

        assertEquals(3, result.score());
        assertEquals(ReflectionDecision.PROBE, result.decision());
        assertTrue(result.feedback().contains("too shallow"));
    }

    @Test
    void planningBuildsPlanWithoutAgentRuntimeGatewayAndKeepsSkillPromptAssembly() {
        List<AiPromptRequest> requests = new ArrayList<>();
        AiGateway aiGateway = request -> {
            requests.add(request);
            return AiGatewayResult.builder()
                    .content("Spring AI alignment summary")
                    .provider("test")
                    .build();
        };
        HybridCompactingChatMemory memory = new HybridCompactingChatMemory(aiGateway, new InterviewRuleBasedScorer(), new DecisionIndex());
        CareerSkillRegistry skillRegistry = new CareerSkillRegistry() {
            @Override
            public Optional<CareerSkill> find(String name) {
                return Optional.empty();
            }

            @Override
            public String promptSection(String name) {
                return "jd-alignment".equals(name) ? "\nSkill prompt: probe for concrete backend tradeoffs." : "";
            }
        };
        InterviewPlanningService service = new InterviewPlanningService(aiGateway, memory, skillRegistry);

        InterviewPlan plan = service.plan(
                "interview:s3",
                "s3",
                CvBO.builder().id(3L).summary("Java Redis").build(),
                "Java Redis backend");

        assertEquals(1, requests.size());
        assertEquals("JD_ALIGNMENT", requests.getFirst().sceneCode());
        assertTrue(requests.getFirst().systemPrompt().contains("Skill prompt: probe for concrete backend tradeoffs."));
        assertEquals("Spring AI alignment summary", plan.alignment().summary());
        assertEquals(List.of("JD_ALIGNMENT", "JAVA_TECH_DEPTH", "PROJECT_REFLECTION"),
                plan.stages().stream().map(InterviewStagePlan::stageName).toList());
        assertTrue(plan.firstQuestion().contains("Please explain one project where you used java"));
        assertTrue(memory.view("interview:s3", 8).decisionContext().contains("Plan-Execute-Reflect plan decision"));
    }

    @Test
    void planningFallsBackToDeterministicQuestionWhenNoMatchedSkillExists() {
        AiGateway aiGateway = request -> AiGatewayResult.builder()
                .content("No direct alignment summary")
                .provider("test")
                .build();
        HybridCompactingChatMemory memory = new HybridCompactingChatMemory(aiGateway, new InterviewRuleBasedScorer(), new DecisionIndex());
        InterviewPlanningService service = new InterviewPlanningService(aiGateway, memory);

        InterviewPlan plan = service.plan(
                "interview:s4",
                "s4",
                CvBO.builder().id(4L).summary("Project ownership").build(),
                "Go");

        assertTrue(plan.firstQuestion().contains("your most relevant backend project"));
    }
}
