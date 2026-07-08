package com.hewei.hzyjy.xunzhi.career.resume.rag;

import lombok.Builder;

@Builder
public record ResumeVectorMatch(
        ResumeVectorDocument document,
        double score
) {
}
