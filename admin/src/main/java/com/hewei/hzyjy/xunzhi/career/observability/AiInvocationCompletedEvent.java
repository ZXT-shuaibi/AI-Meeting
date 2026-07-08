package com.hewei.hzyjy.xunzhi.career.observability;

import java.time.Instant;
import java.util.Map;

public record AiInvocationCompletedEvent(
        String traceId,
        String sessionId,
        Long userId,
        String agentId,
        String agentName,
        String sceneCode,
        String stage,
        String provider,
        String modelOrFlowId,
        Instant startTime,
        Instant endTime,
        long durationMs,
        String inputSummary,
        String outputSummary,
        String requestKey,
        boolean stream,
        Map<String, Object> metadata
) {
}
