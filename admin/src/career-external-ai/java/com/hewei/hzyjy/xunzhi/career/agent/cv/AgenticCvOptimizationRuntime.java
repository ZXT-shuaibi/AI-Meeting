package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.config.CareerOptimizationProperties;
import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO.OptimizationRecord;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@RequiredArgsConstructor
public class AgenticCvOptimizationRuntime {

    public static final String SCORE_GATE_STATE_KEY = "careerOptimizationScoreGate";
    public static final String MAX_ITERATIONS_STATE_KEY = "careerOptimizationMaxIterations";
    public static final String CURRENT_ITERATION_STATE_KEY = "careerOptimizationCurrentIteration";
    private static final int DECLARATIVE_LOOP_MAX_ITERATIONS = 3;
    private static final Map<String, Double> SCORE_GATES = new ConcurrentHashMap<>();
    private static final Map<String, Integer> MAX_ITERATIONS = new ConcurrentHashMap<>();

    private final AgenticCvOptimizationAgent agent;
    private final AiTracePublisher tracePublisher;
    private final CareerOptimizationProperties optimizationProperties;

    public AgenticCvOptimizationRuntime(AgenticCvOptimizationAgent agent) {
        this(agent, null, null);
    }

    public CvOptimizationResult optimize(String memoryId, CvBO cv, String jobDescription, List<String> referenceTemplates) {
        return optimize(memoryId, cv, jobDescription, referenceTemplates, null);
    }

    public AgenticCvOptimizationRuntime(
            AgenticCvOptimizationAgent agent,
            AiTracePublisher tracePublisher) {
        this(agent, tracePublisher, null);
    }

    public AgenticCvOptimizationRuntime(
            AgenticCvOptimizationAgent agent,
            AiTracePublisher tracePublisher,
            CareerOptimizationProperties optimizationProperties) {
        this.agent = agent;
        this.tracePublisher = tracePublisher;
        this.optimizationProperties = optimizationProperties == null ? new CareerOptimizationProperties() : optimizationProperties;
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
            int maxIterations = resolveMaxIterations();
            double scoreGate = resolveScoreGate();
            registerLoopConfig(memoryId, maxIterations, scoreGate);
            AgenticScope existingScope = agent.getAgenticScope(memoryId);
            if (existingScope != null) {
                existingScope.writeState(MAX_ITERATIONS_STATE_KEY, maxIterations);
                existingScope.writeState(SCORE_GATE_STATE_KEY, scoreGate);
                existingScope.writeState(CURRENT_ITERATION_STATE_KEY, 0);
            }
            CvBO optimized = agent.optimizeCv(memoryId, cv, jobDescription, safeReferenceTemplates);
            AgenticScope scope = agent.getAgenticScope(memoryId);
            if (scope != null) {
                scope.writeState(MAX_ITERATIONS_STATE_KEY, maxIterations);
                scope.writeState(SCORE_GATE_STATE_KEY, scoreGate);
            }
            List<CvReview> reviewHistory = extractReviewHistory(scope);
            CvReview review = reviewHistory.isEmpty() ? null : reviewHistory.get(reviewHistory.size() - 1);
            int iterations = reviewHistory.size();
            boolean scoreGatePassed = review != null && review.score() >= scoreGate;
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
            removeLoopConfig(memoryId);
        }
    }

    static double configuredScoreGate(AgenticScope scope) {
        if (scope == null) {
            return 0.8;
        }
        Object configured = scope.readState(SCORE_GATE_STATE_KEY, 0.8d);
        if (configured instanceof Number number) {
            return Math.max(0.0, Math.min(1.0, number.doubleValue()));
        }
        Double mapped = SCORE_GATES.get(String.valueOf(scope.memoryId()));
        if (mapped != null) {
            return Math.max(0.0, Math.min(1.0, mapped));
        }
        return 0.8;
    }

    static int configuredMaxIterations(AgenticScope scope) {
        if (scope == null) {
            return 3;
        }
        Object configured = scope.readState(MAX_ITERATIONS_STATE_KEY, 3);
        if (configured instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        Integer mapped = MAX_ITERATIONS.get(String.valueOf(scope.memoryId()));
        if (mapped != null) {
            return Math.max(1, mapped);
        }
        return 3;
    }

    static int incrementIteration(AgenticScope scope) {
        if (scope == null) {
            return 1;
        }
        int next = 1;
        Object current = scope.readState(CURRENT_ITERATION_STATE_KEY, 0);
        if (current instanceof Number number) {
            next = number.intValue() + 1;
        }
        scope.writeState(CURRENT_ITERATION_STATE_KEY, next);
        return next;
    }

    private int resolveMaxIterations() {
        int configured = optimizationProperties == null ? 3 : optimizationProperties.getMaxIterations();
        int bounded = configured <= 0 ? 3 : configured;
        return Math.min(DECLARATIVE_LOOP_MAX_ITERATIONS, bounded);
    }

    private double resolveScoreGate() {
        double configured = optimizationProperties == null ? 0.8 : optimizationProperties.getScoreGate();
        if (configured <= 0) {
            return 0.8;
        }
        return Math.min(1.0, configured);
    }

    private void registerLoopConfig(String memoryId, int maxIterations, double scoreGate) {
        String key = String.valueOf(memoryId);
        MAX_ITERATIONS.put(key, maxIterations);
        SCORE_GATES.put(key, scoreGate);
    }

    private void removeLoopConfig(String memoryId) {
        String key = String.valueOf(memoryId);
        MAX_ITERATIONS.remove(key);
        SCORE_GATES.remove(key);
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
