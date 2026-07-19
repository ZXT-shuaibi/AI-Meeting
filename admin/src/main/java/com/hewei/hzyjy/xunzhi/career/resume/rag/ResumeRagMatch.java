package com.hewei.hzyjy.xunzhi.career.resume.rag;

import java.util.List;

/** RAG evidence aggregated by one resume, rather than returning anonymous text chunks. */
public record ResumeRagMatch(String resumeId, double score, List<String> evidence) {
    public ResumeRagMatch {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
