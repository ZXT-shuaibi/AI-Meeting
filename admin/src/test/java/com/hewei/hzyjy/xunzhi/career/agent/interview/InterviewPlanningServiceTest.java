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
    void planningUsesSpringAiForStageCoordinationAndFirstQuestion() {
        ScriptedPlanningAiGateway aiGateway = new ScriptedPlanningAiGateway(
                "Spring AI alignment summary",
                """
                        {"stages":[
                          {"stageName":"SPRING_AI_ALIGNMENT","goal":"Validate project fit","questionSeeds":["Java","Redis"]},
                          {"stageName":"SPRING_AI_DEPTH","goal":"Probe reliability depth","questionSeeds":["cache breakdown","latency"]}
                        ]}
                        """,
                """
                        {"question":"Spring AI first question about Redis tradeoffs","rationale":"Start from the strongest matched skill"}
                        """
        );
        HybridCompactingChatMemory memory = new HybridCompactingChatMemory(aiGateway, new InterviewRuleBasedScorer(), new DecisionIndex());
        CareerSkillRegistry skillRegistry = new CareerSkillRegistry() {
            @Override
            public Optional<CareerSkill> find(String name) {
                return Optional.empty();
            }

            @Override
            public String promptSection(String name) {
                return switch (name) {
                    case "jd-alignment" -> "\nSkill prompt: align JD risks.";
                    case "question-probing" -> "\nSkill prompt: probe for concrete backend tradeoffs.";
                    default -> "";
                };
            }
        };
        InterviewPlanningService service = new InterviewPlanningService(aiGateway, memory, skillRegistry);

        InterviewPlan plan = service.plan(
                "interview:s3",
                "s3",
                CvBO.builder().id(3L).summary("Java Redis").build(),
                "Java Redis backend");

        assertEquals(3, aiGateway.requests.size());
        assertEquals("JD_ALIGNMENT", aiGateway.requests.get(0).sceneCode());
        assertEquals("INTERVIEW_COORDINATION", aiGateway.requests.get(1).sceneCode());
        assertEquals("INTERVIEW_COORDINATION", aiGateway.requests.get(2).sceneCode());
        assertTrue(aiGateway.requests.get(0).systemPrompt().contains("Skill prompt: align JD risks."));
        assertTrue(aiGateway.requests.get(1).systemPrompt().contains("Skill prompt: probe for concrete backend tradeoffs."));
        assertTrue(aiGateway.requests.get(2).systemPrompt().contains("Skill prompt: probe for concrete backend tradeoffs."));
        assertEquals("Spring AI alignment summary", plan.alignment().summary());
        assertEquals(List.of("SPRING_AI_ALIGNMENT", "SPRING_AI_DEPTH"),
                plan.stages().stream().map(InterviewStagePlan::stageName).toList());
        assertEquals("Spring AI first question about Redis tradeoffs", plan.firstQuestion());
        assertTrue(memory.view("interview:s3", 8).decisionContext().contains("Plan-Execute-Reflect plan decision"));
    }

    @Test
    void planningFallsBackWhenStageCoordinationAndFirstQuestionResponsesAreInvalid() {
        ScriptedPlanningAiGateway aiGateway = new ScriptedPlanningAiGateway(
                "Fallback alignment summary",
                "{\"stages\":[]}",
                "{\"question\":\"\"}"
        );
        HybridCompactingChatMemory memory = new HybridCompactingChatMemory(aiGateway, new InterviewRuleBasedScorer(), new DecisionIndex());
        InterviewPlanningService service = new InterviewPlanningService(aiGateway, memory);

        InterviewPlan plan = service.plan(
                "interview:s4",
                "s4",
                CvBO.builder().id(4L).summary("Java Redis project ownership").build(),
                "Java Redis backend");

        assertEquals(List.of("JD_ALIGNMENT", "JAVA_TECH_DEPTH", "PROJECT_REFLECTION"),
                plan.stages().stream().map(InterviewStagePlan::stageName).toList());
        assertTrue(plan.firstQuestion().contains("Please explain one project where you used java"));
    }

    private static class ScriptedPlanningAiGateway implements AiGateway {
        private final String alignmentResponse;
        private final String stageResponse;
        private final String questionResponse;
        private final List<AiPromptRequest> requests = new ArrayList<>();

        private ScriptedPlanningAiGateway(String alignmentResponse, String stageResponse, String questionResponse) {
            this.alignmentResponse = alignmentResponse;
            this.stageResponse = stageResponse;
            this.questionResponse = questionResponse;
        }

        @Override
        public AiGatewayResult chat(AiPromptRequest request) {
            requests.add(request);
            String content;
            if ("JD_ALIGNMENT".equals(request.sceneCode())) {
                content = alignmentResponse;
            } else if (request.systemPrompt().contains("multi-stage interview plan")) {
                content = stageResponse;
            } else if (request.systemPrompt().contains("opening interview question")) {
                content = questionResponse;
            } else {
                throw new IllegalStateException("Unexpected prompt: " + request.systemPrompt());
            }
            return AiGatewayResult.builder()
                    .content(content)
                    .provider("test")
                    .build();
        }
    }
}
