package com.hewei.hzyjy.xunzhi.career.agent.interview;

import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.memory.DecisionIndex;
import com.hewei.hzyjy.xunzhi.career.memory.HybridCompactingChatMemory;
import com.hewei.hzyjy.xunzhi.career.memory.InterviewRuleBasedScorer;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import org.junit.jupiter.api.Test;

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
}
