package com.hewei.hzyjy.xunzhi.career.ai;

import lombok.Builder;

@Builder
public record AiGatewayResult(
        String content,
        String provider,
        String model,
        boolean degraded,
        String errorMessage
) {
}
