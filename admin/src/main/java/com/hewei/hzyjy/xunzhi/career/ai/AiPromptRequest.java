package com.hewei.hzyjy.xunzhi.career.ai;

import lombok.Builder;

import java.util.Map;

@Builder
public record AiPromptRequest(
        String sceneCode,
        String sessionId,
        String systemPrompt,
        String userPrompt,
        Map<String, Object> metadata
) {
}
