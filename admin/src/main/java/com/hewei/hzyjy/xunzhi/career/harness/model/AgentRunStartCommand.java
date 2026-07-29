package com.hewei.hzyjy.xunzhi.career.harness.model;

/**
 * 创建一次 Agent 运行时冻结的最小上下文。
 *
 * <p>只保存输入摘要和配置指纹，不在运行总账中重复落库简历、JD、回答等敏感原文。</p>
 */
public record AgentRunStartCommand(
        String businessType,
        String sceneCode,
        Long userId,
        String businessId,
        String sessionId,
        String traceId,
        boolean ragEnabled,
        String inputSummary,
        String configFingerprint) {
}
