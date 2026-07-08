package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;

public record ResumeUploadResult(
        Long resumeId,
        CvBO cv,
        String embeddingStatus,
        int chunkCount,
        String errorMessage
) {
}
