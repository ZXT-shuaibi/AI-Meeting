package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeChunk;

import java.util.List;

public record ResumeEmbeddingResult(
        Long resumeId,
        String status,
        int chunkCount,
        List<ResumeChunk> chunks,
        String errorMessage
) {
    public static ResumeEmbeddingResult completed(Long resumeId, List<ResumeChunk> chunks) {
        return new ResumeEmbeddingResult(resumeId, "COMPLETED", chunks == null ? 0 : chunks.size(), chunks == null ? List.of() : chunks, null);
    }

    public static ResumeEmbeddingResult failed(Long resumeId, String errorMessage) {
        return new ResumeEmbeddingResult(resumeId, "FAILED", 0, List.of(), errorMessage);
    }
}
