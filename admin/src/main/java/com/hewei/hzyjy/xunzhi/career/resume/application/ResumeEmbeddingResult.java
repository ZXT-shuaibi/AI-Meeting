package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeChunk;

import java.util.List;

public record ResumeEmbeddingResult(
        Long resumeId,
        int chunkCount,
        List<ResumeChunk> chunks
) {
}
