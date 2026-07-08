package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CvOptimizationOrchestrator {

    private static final double SCORE_GATE = 0.8;
    private static final int DEFAULT_MAX_ITERATIONS = 3;

    private final CvReviewer reviewer;
    private final ScoredCvTailor tailor;

    public CvOptimizationResult optimize(CvBO cv, String jobDescription, List<String> referenceTemplates, int maxIterations) {
        int boundedMaxIterations = maxIterations <= 0 ? DEFAULT_MAX_ITERATIONS : Math.min(maxIterations, DEFAULT_MAX_ITERATIONS);
        CvBO latestCv = cv;
        CvReview bestReview = null;
        List<CvReview> history = new ArrayList<>();
        String failureReason = null;
        int iterations = 0;

        for (int i = 0; i < boundedMaxIterations; i++) {
            iterations = i + 1;
            try {
                CvReview review = reviewer.review(latestCv, jobDescription, referenceTemplates);
                if (review == null) {
                    failureReason = "Reviewer returned null";
                    break;
                }
                history.add(review);
                bestReview = selectBetter(bestReview, review);
                latestCv.addOptimizationRecord(review.feedback(), review.score());
                latestCv.setAdvice(review.feedback());
                if (review.score() > SCORE_GATE) {
                    return build(latestCv, bestReview, iterations, true, null, history);
                }
                latestCv = tailor.tailor(latestCv, review, referenceTemplates);
                if (latestCv == null) {
                    failureReason = "Tailor returned null";
                    break;
                }
            } catch (Exception ex) {
                failureReason = ex.getMessage();
                break;
            }
        }
        return build(latestCv, bestReview, iterations, false, failureReason, history);
    }

    public CvOptimizationResult optimize(CvBO cv, String jobDescription, List<String> referenceTemplates) {
        return optimize(cv, jobDescription, referenceTemplates, DEFAULT_MAX_ITERATIONS);
    }

    private CvReview selectBetter(CvReview current, CvReview candidate) {
        if (current == null || candidate.score() >= current.score()) {
            return candidate;
        }
        return current;
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
