package com.hewei.hzyjy.xunzhi.career.resume.application;

import java.util.List;

/** A ranked, user-owned resume version selected for one JD matching task. */
public record JobMatchCandidate(
        Long resumeId,
        String originalFilename,
        int rank,
        int matchScore,
        String recommendationLevel,
        List<String> matchedPoints,
        List<String> missingPoints
) {
    public JobMatchCandidate {
        matchedPoints = matchedPoints == null ? List.of() : List.copyOf(matchedPoints);
        missingPoints = missingPoints == null ? List.of() : List.copyOf(missingPoints);
    }
}
