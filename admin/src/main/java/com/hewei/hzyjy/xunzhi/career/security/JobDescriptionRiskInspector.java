package com.hewei.hzyjy.xunzhi.career.security;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * JD 风险的确定性首轮检测器。
 *
 * <p>检测结果只返回风险类别，不携带命中的原始片段；调用方也不得记录匹配文本，
 * 以免把不可信的注入载荷再次持久化到日志或审计表。</p>
 */
final class JobDescriptionRiskInspector {
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|file://|www\\.)\\S+");
    private static final Pattern BASE64 = Pattern.compile("(?i)\\b[A-Za-z0-9+/]{80,}={0,2}\\b");
    private static final Pattern PSEUDO_XML = Pattern.compile("(?i)</?(?:system|developer|assistant|tool|instruction)[^>]*>");
    private static final Pattern REPEATED = Pattern.compile("(.)\\1{20,}");

    JobDescriptionRiskReport inspect(String text, int maxLineLength) {
        String value = text == null ? "" : text;
        // 检测器自身再次归一化词间空白，避免某个调用方遗漏预处理时被零宽字符、全角空格或连续空格绕过。
        String lower = value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Z}\\u200B-\\u200D\\uFEFF]+", " ")
                .trim();
        EnumSet<JobDescriptionRiskSignal> signals = EnumSet.noneOf(JobDescriptionRiskSignal.class);
        if (containsAny(lower, "ignore previous", "ignore all", "disregard previous", "bypass rules",
                "\u5ffd\u7565\u524d\u9762", "\u5ffd\u7565\u6240\u6709", "\u5ffd\u7565\u89c4\u5219")) {
            signals.add(JobDescriptionRiskSignal.INSTRUCTION_OVERRIDE);
        }
        if (containsAny(lower, "system prompt", "developer message", "assistant message",
                "\u7cfb\u7edf\u63d0\u793a\u8bcd", "\u5f00\u53d1\u8005\u6d88\u606f")) {
            signals.add(JobDescriptionRiskSignal.SYSTEM_PROMPT_REQUEST);
        }
        if (containsAny(lower, "you are now", "act as a", "roleplay as", "\u626e\u6f14\u7cfb\u7edf")) {
            signals.add(JobDescriptionRiskSignal.ROLE_IMPERSONATION);
        }
        if (containsAny(lower, "give 100", "score 100", "directly give", "\u76f4\u63a5\u7ed9", "\u7ed9 100 \u5206", "\u7ed9\u6ee1\u5206")) {
            signals.add(JobDescriptionRiskSignal.SCORE_MANIPULATION);
        }
        if (containsAny(lower, "call tool", "tool call", "invoke tool", "\u8c03\u7528\u5de5\u5177")) {
            signals.add(JobDescriptionRiskSignal.TOOL_CALL_REQUEST);
        }
        if (containsAny(lower, "read file", "read database", "exfiltrate", "leak data",
                "\u8bfb\u53d6\u6587\u4ef6", "\u6cc4\u9732", "\u5bfc\u51fa\u6570\u636e")) {
            signals.add(JobDescriptionRiskSignal.DATA_EXFILTRATION);
        }
        if (containsAny(lower, "execute sql", "powershell", "bash -c", "shell command", "curl ", "\u6267\u884c sql")) {
            signals.add(JobDescriptionRiskSignal.COMMAND_EXECUTION);
        }
        if (BASE64.matcher(value).find() || lower.contains("base64")) signals.add(JobDescriptionRiskSignal.ENCODED_PAYLOAD);
        if (URL.matcher(value).find()) signals.add(JobDescriptionRiskSignal.EXTERNAL_URL);
        if (PSEUDO_XML.matcher(value).find()) signals.add(JobDescriptionRiskSignal.PSEUDO_XML);
        if (REPEATED.matcher(value).find() || hasExcessiveLine(value, maxLineLength)) {
            signals.add(REPEATED.matcher(value).find()
                    ? JobDescriptionRiskSignal.REPETITIVE_TEXT
                    : JobDescriptionRiskSignal.EXCESSIVE_LINE_LENGTH);
        }
        return new JobDescriptionRiskReport(level(signals), signals.stream().sorted().toList());
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) if (value.contains(candidate)) return true;
        return false;
    }

    private static boolean hasExcessiveLine(String value, int maxLineLength) {
        int bound = Math.max(128, maxLineLength);
        for (String line : value.split("[\\r\\n]+")) if (line.length() > bound) return true;
        return false;
    }

    private static JobDescriptionRiskLevel level(Set<JobDescriptionRiskSignal> signals) {
        if (signals.stream().anyMatch(signal -> switch (signal) {
            case INSTRUCTION_OVERRIDE, SYSTEM_PROMPT_REQUEST, ROLE_IMPERSONATION,
                    SCORE_MANIPULATION, TOOL_CALL_REQUEST, DATA_EXFILTRATION,
                    COMMAND_EXECUTION -> true;
            default -> false;
        })) return JobDescriptionRiskLevel.HIGH;
        return signals.isEmpty() ? JobDescriptionRiskLevel.LOW : JobDescriptionRiskLevel.MEDIUM;
    }
}
