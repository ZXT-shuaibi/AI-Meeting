package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import dev.langchain4j.agentic.AgenticServices;
import org.junit.jupiter.api.Test;

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
}
