package com.hewei.hzyjy.xunzhi.career.security;

import java.util.List;

/** 后续 RAG 与 Agent 只能消费本对象，不得再使用原始 JD。 */
public record JobDescriptionSafetyContext(
        JobDescriptionSafetyDecision decision,
        JobDescriptionRiskLevel riskLevel,
        List<JobDescriptionRiskSignal> riskSignals,
        String normalizedInputDigest,
        String safeRetrievalQuery,
        String safeOptimizationContext,
        boolean suspiciousInstructionDetected,
        int filteredInstructionCount,
        int structuredFieldsCount,
        boolean fallbackUsed,
        String contextFingerprint,
        JobDescriptionProfile profile) {
    public JobDescriptionSafetyContext {
        riskSignals = riskSignals == null ? List.of() : List.copyOf(riskSignals);
        normalizedInputDigest = normalizedInputDigest == null ? "" : normalizedInputDigest;
        safeRetrievalQuery = safeRetrievalQuery == null ? "" : safeRetrievalQuery;
        safeOptimizationContext = safeOptimizationContext == null ? safeRetrievalQuery : safeOptimizationContext;
        contextFingerprint = contextFingerprint == null ? "" : contextFingerprint;
    }
    /**
     * 兼容旧调用方的构造方法：旧链路尚未传递风险元数据时，仍可构造受限的安全上下文，
     * 但不能借此恢复或输出原始 JD。
     */
    public JobDescriptionSafetyContext(
            JobDescriptionSafetyDecision decision,
            String safeRetrievalQuery,
            boolean suspiciousInstructionDetected,
            int filteredInstructionCount,
            int structuredFieldsCount,
            boolean fallbackUsed,
            String contextFingerprint,
            JobDescriptionProfile profile) {
        this(decision,
                suspiciousInstructionDetected ? JobDescriptionRiskLevel.HIGH : JobDescriptionRiskLevel.LOW,
                List.of(), "", safeRetrievalQuery, safeRetrievalQuery,
                suspiciousInstructionDetected, filteredInstructionCount, structuredFieldsCount,
                fallbackUsed, contextFingerprint, profile);
    }
    public boolean rejected() { return decision == JobDescriptionSafetyDecision.REJECTED; }
    public boolean filtered() { return decision == JobDescriptionSafetyDecision.FILTERED; }
}
