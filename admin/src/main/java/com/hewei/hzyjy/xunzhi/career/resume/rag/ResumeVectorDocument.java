package com.hewei.hzyjy.xunzhi.career.resume.rag;

import lombok.Builder;

import java.util.Map;

@Builder
public record ResumeVectorDocument(
        String id,
        String text,
        float[] vector,
        Map<String, String> metadata
) {
}
