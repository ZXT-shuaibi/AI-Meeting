package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import dev.langchain4j.agentic.AgenticServices;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgenticCvOptimizationRuntimeIT {

    @Test
    void createsLangChain4jLoopAgentRuntimeForCvOptimization() {
        AgenticCvOptimizationAgent agent = AgenticServices.createAgenticSystem(
                AgenticCvOptimizationAgent.class,
                new ScriptedCvChatModel()
        );
        AgenticCvOptimizationRuntime runtime = new AgenticCvOptimizationRuntime(agent);

        CvOptimizationResult result = runtime.optimize(
                "resume:agentic:1",
                CvBO.builder().name("candidate").summary("Java backend Redis project").build(),
                "Java Redis backend engineer",
                List.of("Use quantified backend impact")
        );

        assertTrue(result.scoreGatePassed());
        assertEquals(2, result.iterations());
        assertTrue(result.cv().getSummary().contains("Optimized by Agentic tailor"));
        assertTrue(result.cv().getAdvice().contains("score"));
    }

    @Test
    void preservesReviewHistoryAndStreamsProgressForEachLoopIteration() {
        AgenticCvOptimizationAgent agent = AgenticServices.createAgenticSystem(
                AgenticCvOptimizationAgent.class,
                new ScriptedCvChatModel()
        );
        AgenticCvOptimizationRuntime runtime = new AgenticCvOptimizationRuntime(agent);
        List<CvReview> progressReviews = new ArrayList<>();

        CvOptimizationResult result = runtime.optimize(
                "resume:agentic:history",
                CvBO.builder().name("candidate").summary("Java backend Redis project").build(),
                "Java Redis backend engineer",
                List.of("Use quantified backend impact"),
                progressReviews::add
        );

        assertEquals(2, result.reviewHistory().size());
        assertEquals("score 0.72, add quantified backend impact", result.reviewHistory().get(0).feedback());
        assertEquals("score 0.86, ready for interview", result.reviewHistory().get(1).feedback());
        assertEquals(result.reviewHistory(), progressReviews);
        assertEquals(2, result.cv().getOptimizationHistory().size());
    }
}
