package com.hewei.hzyjy.xunzhi.career.memory;

import lombok.Builder;

import java.util.List;

@Builder
public record CompactedMemoryView(
        String memoryId,
        List<MemoryMessage> messages,
        List<DecisionEntry> decisions,
        String decisionContext
) {
}
