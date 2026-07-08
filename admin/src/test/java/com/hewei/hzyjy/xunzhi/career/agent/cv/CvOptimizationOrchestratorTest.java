package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CvOptimizationOrchestratorTest {

    @Test
    void exitsWhenReviewScorePassesGate() {
        CvBO original = CvBO.builder().name("candidate").summary("java backend").build();
        AtomicInteger reviewCalls = new AtomicInteger();
        CvReviewer reviewer = (cv, jd, templates) -> new CvReview(
                reviewCalls.incrementAndGet() == 1 ? 0.72 : 0.83,
                "feedback-" + reviewCalls.get()
        );
        ScoredCvTailor tailor = (cv, review, templates) -> cv.toBuilder()
                .advice(review.feedback())
                .summary(cv.getSummary() + " optimized")
                .build();
        CvOptimizationOrchestrator orchestrator = new CvOptimizationOrchestrator(reviewer, tailor);

        CvOptimizationResult result = orchestrator.optimize(original, "Java JD", List.of("template"), 3);

        assertEquals(2, result.iterations());
        assertEquals(0.83, result.bestReview().score());
        assertEquals("feedback-2", result.cv().getAdvice());
    }

    @Test
    void stopsAtMaxIterationsAndReturnsLatestReviewedCvWhenGateNotMet() {
        CvBO original = CvBO.builder().name("candidate").summary("java backend").build();
        AtomicInteger tailorCalls = new AtomicInteger();
        CvReviewer reviewer = (cv, jd, templates) -> new CvReview(0.6, "keep improving");
        ScoredCvTailor tailor = (cv, review, templates) -> {
            tailorCalls.incrementAndGet();
            return cv.toBuilder().summary("round-" + tailorCalls.get()).build();
        };
        CvOptimizationOrchestrator orchestrator = new CvOptimizationOrchestrator(reviewer, tailor);

        CvOptimizationResult result = orchestrator.optimize(original, "Java JD", List.of(), 3);

        assertEquals(3, result.iterations());
        assertEquals("round-2", result.cv().getSummary());
        assertEquals(2, tailorCalls.get());
    }

    @Test
    void returnsLatestUsableResultWhenTailorFails() {
        CvBO original = CvBO.builder().name("candidate").summary("java backend").build();
        CvReviewer reviewer = (cv, jd, templates) -> new CvReview(0.7, "feedback");
        ScoredCvTailor tailor = (cv, review, templates) -> {
            throw new IllegalStateException("llm parse failed");
        };
        CvOptimizationOrchestrator orchestrator = new CvOptimizationOrchestrator(reviewer, tailor);

        CvOptimizationResult result = orchestrator.optimize(original, "Java JD", List.of(), 3);

        assertSame(original, result.cv());
        assertEquals(1, result.iterations());
        assertEquals("llm parse failed", result.failureReason());
    }
}
