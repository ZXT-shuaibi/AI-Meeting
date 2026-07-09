package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO.OptimizationRecord;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@RequiredArgsConstructor
public class AgenticCvOptimizationRuntime {

    private final AgenticCvOptimizationAgent agent;
    private final AiTracePublisher tracePublisher;

    public AgenticCvOptimizationRuntime(AgenticCvOptimizationAgent agent) {
        this(agent, null);
    }

    public CvOptimizationResult optimize(String memoryId, CvBO cv, String jobDescription, List<String> referenceTemplates) {
        return optimize(memoryId, cv, jobDescription, referenceTemplates, null);
    }

    public CvOptimizationResult optimize(
            String memoryId,
            CvBO cv,
            String jobDescription,
            List<String> referenceTemplates,
            Consumer<CvReview> progressCallback) {
        String traceId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        List<String> safeReferenceTemplates = referenceTemplates == null ? List.of() : referenceTemplates;
        if (tracePublisher != null) {
            tracePublisher.started(traceId, "RESUME_TAILOR", memoryId, "langchain4j-agentic", "AgenticCvOptimizationAgent",
                    "cv=" + cv + ", jd=" + jobDescription);
        }
        try {
            AgenticCvOptimizationAgent.registerProgressCallback(memoryId, progressCallback);
            CvBO optimized = agent.optimizeCv(memoryId, cv, jobDescription, safeReferenceTemplates);
            AgenticScope scope = agent.getAgenticScope(memoryId);
            List<CvReview> reviewHistory = extractReviewHistory(scope);
            CvReview review = reviewHistory.isEmpty() ? null : reviewHistory.get(reviewHistory.size() - 1);
            int iterations = reviewHistory.size();
            boolean scoreGatePassed = review != null && review.score() > AgenticCvOptimizationAgent.SCORE_GATE;
            String failureReason = scoreGatePassed ? null : "Score gate not reached after LangChain4j agentic loop";
            CvOptimizationResult result = CvOptimizationResult.builder()
                    .cv(applyReviewHistory(optimized, reviewHistory))
                    .bestReview(bestReview(reviewHistory, review))
                    .iterations(iterations)
                    .scoreGatePassed(scoreGatePassed)
                    .failureReason(failureReason)
                    .reviewHistory(List.copyOf(reviewHistory))
                    .build();
            if (tracePublisher != null) {
                tracePublisher.completed(traceId, "RESUME_TAILOR", memoryId, "langchain4j-agentic", "AgenticCvOptimizationAgent",
                        start, String.valueOf(result));
            }
            return result;
        } catch (RuntimeException ex) {
            if (tracePublisher != null) {
                tracePublisher.failed(traceId, "RESUME_TAILOR", memoryId, "langchain4j-agentic", "AgenticCvOptimizationAgent", start, ex);
            }
            throw ex;
        } finally {
            AgenticCvOptimizationAgent.removeProgressCallback(memoryId);
        }
    }

    @SuppressWarnings("unchecked")
    private List<CvReview> extractReviewHistory(AgenticScope scope) {
        if (scope == null) {
            return List.of();
        }
        Object stateHistory = scope.readState(AgenticCvOptimizationAgent.REVIEW_HISTORY_STATE_KEY, List.of());
        if (stateHistory instanceof List<?> entries && !entries.isEmpty()) {
            return List.copyOf((List<CvReview>) entries);
        }
        List<CvReview> reviews = new ArrayList<>();
        for (AgentInvocation invocation : scope.agentInvocations(AgenticCvReviewAgent.class)) {
            if (invocation != null && invocation.output() instanceof CvReview review) {
                reviews.add(review);
            }
        }
        if (reviews.isEmpty()) {
            CvReview latest = (CvReview) scope.readState("cvReview", null);
            if (latest != null) {
                reviews.add(latest);
            }
        }
        return List.copyOf(reviews);
    }

    private CvReview bestReview(List<CvReview> reviewHistory, CvReview fallback) {
        CvReview best = fallback;
        for (CvReview candidate : reviewHistory) {
            if (best == null || candidate.score() >= best.score()) {
                best = candidate;
            }
        }
        return best;
    }

    private CvBO applyReviewHistory(CvBO optimized, List<CvReview> reviewHistory) {
        if (optimized == null || reviewHistory == null || reviewHistory.isEmpty()) {
            return optimized;
        }
        List<OptimizationRecord> existingHistory = optimized.getOptimizationHistory() == null
                ? List.of()
                : List.copyOf(optimized.getOptimizationHistory());
        if (existingHistory.size() >= reviewHistory.size()) {
            return optimized;
        }
        CvBO updated = optimized;
        for (int i = existingHistory.size(); i < reviewHistory.size(); i++) {
            CvReview review = reviewHistory.get(i);
            updated.addOptimizationRecord(review.feedback(), review.score());
            updated.setAdvice(review.feedback());
        }
        return updated;
    }
}
