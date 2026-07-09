package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;

import java.util.List;
import java.util.function.Function;

public interface AgenticCvOptimizationAgent extends AgenticScopeAccess {

    double SCORE_GATE = 0.8;

    @LoopAgent(
            outputKey = "cv",
            maxIterations = 3,
            subAgents = {AgenticCvReviewAgent.class, AgenticScoredCvTailorAgent.class}
    )
    CvBO optimizeCv(@MemoryId String memoryId,
                    @V("cv") CvBO cv,
                    @V("jobDescription") String jobDescription,
                    @V("referenceTemplates") List<String> referenceTemplates);

    @ExitCondition(testExitAtLoopEnd = true)
    static boolean exitCondition(AgenticScope agenticScope) {
        CvReview review = (CvReview) agenticScope.readState("cvReview", null);
        if (review == null) {
            return false;
        }
        Object cvValue = agenticScope.readState("cv", null);
        if (cvValue instanceof CvBO cv) {
            cv.addOptimizationRecord(review.feedback(), review.score());
            cv.setAdvice(review.feedback());
        }
        return review.score() > SCORE_GATE;
    }

    @ChatMemoryProviderSupplier
    static ChatMemory chatMemory(Object memoryId) {
        return AgenticCvMemoryProvider.chatMemory(memoryId);
    }

    static void registerChatMemoryProvider(Function<Object, ChatMemory> provider) {
        AgenticCvMemoryProvider.register(provider);
    }

    static void resetChatMemoryProvider() {
        AgenticCvMemoryProvider.reset();
    }
}
