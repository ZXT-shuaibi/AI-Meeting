package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import lombok.Builder;

import java.util.List;

@Builder
public record CvOptimizationResult(
        CvBO cv,
        CvReview bestReview,
        int iterations,
        boolean scoreGatePassed,
        String failureReason,
        List<CvReview> reviewHistory
) {
}
