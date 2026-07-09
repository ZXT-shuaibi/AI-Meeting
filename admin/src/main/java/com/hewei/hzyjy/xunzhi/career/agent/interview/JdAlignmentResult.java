package com.hewei.hzyjy.xunzhi.career.agent.interview;

import lombok.Builder;

import java.util.List;

@Builder
public record JdAlignmentResult(
        double matchScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        String summary
) {
    public JdAlignmentResult {
        matchedSkills = matchedSkills == null ? List.of() : List.copyOf(matchedSkills);
        missingSkills = missingSkills == null ? List.of() : List.copyOf(missingSkills);
        summary = summary == null ? "" : summary;
    }
}
