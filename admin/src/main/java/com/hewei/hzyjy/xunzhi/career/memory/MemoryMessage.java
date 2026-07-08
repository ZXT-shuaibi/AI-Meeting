package com.hewei.hzyjy.xunzhi.career.memory;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;

@Builder
public record MemoryMessage(
        MemoryRole role,
        String content,
        Instant timestamp,
        Map<String, Object> metadata
) {
    public MemoryMessage {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
