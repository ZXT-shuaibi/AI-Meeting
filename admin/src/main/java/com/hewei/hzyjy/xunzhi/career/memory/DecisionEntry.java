package com.hewei.hzyjy.xunzhi.career.memory;

import java.time.Instant;

public record DecisionEntry(
        int messageIndex,
        String summary,
        Instant timestamp
) {
}
