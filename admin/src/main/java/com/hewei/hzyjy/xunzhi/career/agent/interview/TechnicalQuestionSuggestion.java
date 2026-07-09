package com.hewei.hzyjy.xunzhi.career.agent.interview;

import lombok.Builder;

@Builder
public record TechnicalQuestionSuggestion(
        String question,
        String rationale
) {
}
