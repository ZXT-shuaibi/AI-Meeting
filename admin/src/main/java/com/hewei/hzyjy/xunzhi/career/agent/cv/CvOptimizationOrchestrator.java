package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.alibaba.fastjson2.JSON;
import com.hewei.hzyjy.xunzhi.career.config.CareerOptimizationProperties;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
public class CvOptimizationOrchestrator {

    private static final double DEFAULT_SCORE_GATE = 0.8;
    private static final int DEFAULT_MAX_ITERATIONS = 3;

    private final CvReviewer reviewer;
    private final ScoredCvTailor tailor;
    private final CareerOptimizationProperties optimizationProperties;

    @Autowired
    public CvOptimizationOrchestrator(
            CvReviewer reviewer,
            ScoredCvTailor tailor,
            ObjectProvider<CareerOptimizationProperties> optimizationPropertiesProvider) {
        this(reviewer, tailor, optimizationPropertiesProvider.getIfAvailable(CareerOptimizationProperties::new));
    }

    public CvOptimizationOrchestrator(CvReviewer reviewer, ScoredCvTailor tailor) {
        this(reviewer, tailor, new CareerOptimizationProperties());
    }

    public CvOptimizationOrchestrator(
            CvReviewer reviewer,
            ScoredCvTailor tailor,
            CareerOptimizationProperties optimizationProperties) {
        this.reviewer = reviewer;
        this.tailor = tailor;
        this.optimizationProperties = optimizationProperties == null ? new CareerOptimizationProperties() : optimizationProperties;
    }

    public CvOptimizationResult optimize(CvBO cv, String jobDescription, List<String> referenceTemplates, int maxIterations) {
        return optimize(cv, jobDescription, referenceTemplates, maxIterations, null);
    }

    public CvOptimizationResult optimize(
            CvBO cv,
            String jobDescription,
            List<String> referenceTemplates,
            Consumer<CvReview> progressCallback) {
        return optimize(cv, jobDescription, referenceTemplates, 0, progressCallback);
    }

    public CvOptimizationResult optimize(
            CvBO cv,
            String jobDescription,
            List<String> referenceTemplates,
            int maxIterations,
            Consumer<CvReview> progressCallback) {
        int boundedMaxIterations = resolveMaxIterations(maxIterations);
        double scoreGate = resolveScoreGate();
        CvBO latestCv = copyCv(cv);
        CvReview bestReview = null;
        List<CvReview> history = new ArrayList<>();
        String failureReason = null;
        int iterations = 0;

        for (int i = 0; i < boundedMaxIterations; i++) {
            iterations = i + 1;
            try {
                CvReview review = reviewer.review(copyCv(latestCv), jobDescription, referenceTemplates);
                if (review == null) {
                    failureReason = "Reviewer returned null";
                    break;
                }
                history.add(review);
                if (progressCallback != null) {
                    progressCallback.accept(review);
                }
                bestReview = selectBetter(bestReview, review);
                latestCv = copyCv(latestCv);
                latestCv.addOptimizationRecord(review.feedback(), review.score());
                latestCv.setAdvice(review.feedback());
                if (review.score() >= scoreGate) {
                    return build(latestCv, bestReview, iterations, true, null, history);
                }
                if (i == boundedMaxIterations - 1) {
                    failureReason = "Score gate not reached after max iterations";
                    break;
                }
                CvBO tailored = tailor.tailor(copyCv(latestCv), review, referenceTemplates);
                if (tailored == null) {
                    failureReason = "Tailor returned null";
                    break;
                }
                latestCv = copyCv(tailored);
            } catch (Exception ex) {
                failureReason = ex.getMessage();
                break;
            }
        }
        return build(latestCv, bestReview, iterations, false, failureReason, history);
    }

    public CvOptimizationResult optimize(CvBO cv, String jobDescription, List<String> referenceTemplates) {
        return optimize(cv, jobDescription, referenceTemplates, 0, null);
    }

    private int resolveMaxIterations(int requestedMaxIterations) {
        int configured = optimizationProperties == null ? DEFAULT_MAX_ITERATIONS : optimizationProperties.getMaxIterations();
        int safeConfigured = configured <= 0 ? DEFAULT_MAX_ITERATIONS : configured;
        if (requestedMaxIterations <= 0) {
            return safeConfigured;
        }
        return Math.min(requestedMaxIterations, safeConfigured);
    }

    private double resolveScoreGate() {
        double configured = optimizationProperties == null ? DEFAULT_SCORE_GATE : optimizationProperties.getScoreGate();
        if (configured <= 0) {
            return DEFAULT_SCORE_GATE;
        }
        return Math.min(1.0, configured);
    }

    private CvReview selectBetter(CvReview current, CvReview candidate) {
        if (current == null || candidate.score() >= current.score()) {
            return candidate;
        }
        return current;
    }

    private CvBO copyCv(CvBO cv) {
        if (cv == null) {
            return null;
        }
        return JSON.parseObject(JSON.toJSONString(cv), CvBO.class);
    }

    private CvOptimizationResult build(
            CvBO cv,
            CvReview bestReview,
            int iterations,
            boolean scoreGatePassed,
            String failureReason,
            List<CvReview> history) {
        return CvOptimizationResult.builder()
                .cv(cv)
                .bestReview(bestReview)
                .iterations(iterations)
                .scoreGatePassed(scoreGatePassed)
                .failureReason(failureReason)
                .reviewHistory(List.copyOf(history))
                .build();
    }
}
