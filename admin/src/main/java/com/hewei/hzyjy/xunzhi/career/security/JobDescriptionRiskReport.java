package com.hewei.hzyjy.xunzhi.career.security;

import java.util.List;

/** JD 静态风险检测结果；刻意不保存任何原始用户文本。 */
public record JobDescriptionRiskReport(
        JobDescriptionRiskLevel riskLevel,
        List<JobDescriptionRiskSignal> signals) {
    public JobDescriptionRiskReport {
        signals = signals == null ? List.of() : List.copyOf(signals);
    }

    public boolean suspicious() {
        return !signals.isEmpty();
    }

    public boolean hasExecutableInstruction() {
        return signals.stream().anyMatch(signal -> switch (signal) {
            case INSTRUCTION_OVERRIDE, SYSTEM_PROMPT_REQUEST, ROLE_IMPERSONATION,
                    SCORE_MANIPULATION, TOOL_CALL_REQUEST, DATA_EXFILTRATION,
                    COMMAND_EXECUTION -> true;
            default -> false;
        });
    }
}
