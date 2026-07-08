package com.hewei.hzyjy.xunzhi.career.resume.rag;

import lombok.Builder;

import java.util.Map;

@Builder
public record ResumeChunk(
        String chunkType,
        String content,
        Map<String, String> metadata
) {
}
