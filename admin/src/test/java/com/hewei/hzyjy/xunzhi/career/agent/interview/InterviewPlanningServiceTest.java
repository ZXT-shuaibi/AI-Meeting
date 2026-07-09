package com.hewei.hzyjy.xunzhi.career.agent.interview;

import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.ai.AgentRuntimeGateway;
import com.hewei.hzyjy.xunzhi.career.memory.DecisionIndex;
import com.hewei.hzyjy.xunzhi.career.memory.HybridCompactingChatMemory;
import com.hewei.hzyjy.xunzhi.career.memory.InterviewRuleBasedScorer;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewPlanningServiceTest {

    @Test
    void reflectionUsesLlmDecisionAndWritesDecisionMemory() {
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
    void reflectionFallsBackWhenAgenticResultMissesDecision() {
        AiGateway aiGateway = request -> AiGatewayResult.builder()
                .content("{\"score\":8,\"decision\":\"STAGE_FINISH\",\"feedback\":\"Fallback is valid.\"}")
                .provider("test")
                .build();
        HybridCompactingChatMemory memory = new HybridCompactingChatMemory(aiGateway, new InterviewRuleBasedScorer(), new DecisionIndex());
        AgentRuntimeGateway invalidReflector = new AgentRuntimeGateway() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T invoke(String agentName, String methodName, Map<String, Object> variables, Class<T> responseType) {
                if ("InterviewReflectorAgent".equals(agentName)) {
                    return (T) new ReflectionResult(5, null, "invalid missing decision", java.util.List.of());
                }
                throw new IllegalStateException("Unexpected agent: " + agentName);
            }
        };
        InterviewPlanningService service = new InterviewPlanningService(aiGateway, memory, provider(invalidReflector));

        ReflectionResult result = service.reflect("interview:s2", "How did you use Redis?", "I used cache aside.", CvBO.builder().id(2L).summary("Java Redis").build());

        assertEquals(8, result.score());
        assertEquals(ReflectionDecision.STAGE_FINISH, result.decision());
        assertTrue(result.feedback().contains("Fallback is valid"));
    }

    @Test
    void planningUsesJavaTechInterviewerAgentForFirstQuestionOnly() {
        AiGateway aiGateway = request -> AiGatewayResult.builder()
                .content("{\"score\":8,\"decision\":\"NEXT\",\"feedback\":\"ok\"}")
                .provider("test")
                .build();
        HybridCompactingChatMemory memory = new HybridCompactingChatMemory(aiGateway, new InterviewRuleBasedScorer(), new DecisionIndex());
        AgentRuntimeGateway gateway = new AgentRuntimeGateway() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T invoke(String agentName, String methodName, Map<String, Object> variables, Class<T> responseType) {
                if ("JavaTechInterviewerAgent".equals(agentName)) {
                    return (T) TechnicalQuestionSuggestion.builder()
                            .question("Agentic Java question about Redis hot key mitigation")
                            .rationale("Use JD alignment and stage seeds")
                            .build();
                }
                throw new IllegalStateException("No agent: " + agentName);
            }
        };
        InterviewPlanningService service = new InterviewPlanningService(aiGateway, memory, provider(gateway));

        InterviewPlan plan = service.plan("interview:s3", "s3", CvBO.builder().id(3L).summary("Java Redis").build(), "Java Redis backend");

        assertEquals("Agentic Java question about Redis hot key mitigation", plan.firstQuestion());
    }

    @Test
    void planningFallsBackWhenJavaTechInterviewerAgentFails() {
        AiGateway aiGateway = request -> AiGatewayResult.builder()
                .content("{\"score\":8,\"decision\":\"NEXT\",\"feedback\":\"ok\"}")
                .provider("test")
                .build();
        HybridCompactingChatMemory memory = new HybridCompactingChatMemory(aiGateway, new InterviewRuleBasedScorer(), new DecisionIndex());
        AgentRuntimeGateway gateway = new AgentRuntimeGateway() {
            @Override
            public <T> T invoke(String agentName, String methodName, Map<String, Object> variables, Class<T> responseType) {
                throw new IllegalStateException("agent unavailable");
            }
        };
        InterviewPlanningService service = new InterviewPlanningService(aiGateway, memory, provider(gateway));

        InterviewPlan plan = service.plan("interview:s4", "s4", CvBO.builder().id(4L).summary("Java Redis").build(), "Java Redis backend");

        assertTrue(plan.firstQuestion().contains("Please explain one project"));
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
