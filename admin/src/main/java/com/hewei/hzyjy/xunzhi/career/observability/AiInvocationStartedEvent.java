package com.hewei.hzyjy.xunzhi.career.observability;

import java.time.Instant;
import java.util.Map;

public record AiInvocationStartedEvent(
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
        String inputSummary,
        String requestKey,
        boolean stream,
        Map<String, Object> metadata
) {
}
