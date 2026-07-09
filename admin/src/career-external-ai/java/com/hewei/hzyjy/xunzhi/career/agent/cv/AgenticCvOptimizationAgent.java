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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public interface AgenticCvOptimizationAgent extends AgenticScopeAccess {

    double SCORE_GATE = 0.8;
    String REVIEW_HISTORY_STATE_KEY = "cvReviewHistory";
    Map<String, Consumer<CvReview>> PROGRESS_CALLBACKS = new ConcurrentHashMap<>();

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
        List<CvReview> reviewHistory = readReviewHistory(agenticScope);
        reviewHistory.add(review);
        agenticScope.writeState(REVIEW_HISTORY_STATE_KEY, List.copyOf(reviewHistory));
        Consumer<CvReview> progressCallback = PROGRESS_CALLBACKS.get(String.valueOf(agenticScope.memoryId()));
        if (progressCallback != null) {
            progressCallback.accept(review);
        }
        Object cvValue = agenticScope.readState("cv", null);
        if (cvValue instanceof CvBO cv) {
            cv.addOptimizationRecord(review.feedback(), review.score());
            cv.setAdvice(review.feedback());
        }
        return review.score() > SCORE_GATE;
    }

    @SuppressWarnings("unchecked")
    private static List<CvReview> readReviewHistory(AgenticScope agenticScope) {
        Object history = agenticScope.readState(REVIEW_HISTORY_STATE_KEY, List.of());
        if (history instanceof List<?> entries) {
            return new ArrayList<>((List<CvReview>) entries);
        }
        return new ArrayList<>();
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

    static void registerProgressCallback(Object memoryId, Consumer<CvReview> callback) {
        String key = String.valueOf(memoryId);
        if (callback == null) {
            PROGRESS_CALLBACKS.remove(key);
            return;
        }
        PROGRESS_CALLBACKS.put(key, callback);
    }

    static void removeProgressCallback(Object memoryId) {
        PROGRESS_CALLBACKS.remove(String.valueOf(memoryId));
    }
}
