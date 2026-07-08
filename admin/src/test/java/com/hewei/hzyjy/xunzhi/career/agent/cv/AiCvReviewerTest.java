package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCvReviewerTest {

    @Test
    void usesStructuredLlmScoreBeforeHeuristicFallback() {
        AiCvReviewer reviewer = new AiCvReviewer(request -> AiGatewayResult.builder()
                .content("{\"score\":0.86,\"feedback\":\"Strong JD fit, add metrics.\"}")
                .provider("test")
                .build());

        CvReview review = reviewer.review(CvBO.builder().summary("java redis").build(), "python frontend", List.of());

        assertEquals(0.86, review.score());
        assertTrue(review.feedback().contains("Strong JD fit"));
    }
}
