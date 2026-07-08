package com.hewei.hzyjy.xunzhi.career.observability;

import java.time.Instant;
import java.util.Map;

public record AiToolExecutionEvent(
        String traceId,
        String sessionId,
        String agentId,
        String sceneCode,
        String toolName,
        String toolInput,
        String toolOutput,
        boolean success,
        long executionTimeMs,
        int invocationOrder,
        String errorMessage,
        Instant eventTime,
        Map<String, Object> metadata
) {
}
