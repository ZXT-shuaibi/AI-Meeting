package com.hewei.hzyjy.xunzhi.career.resume.application;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将模型常返回的“年-月”格式简历日期规范化为 {@code LocalDate} 可解析的完整日期。
 *
 * <p>教育和工作经历往往只有月份精度，例如 {@code 2024-09}。在不改变原始语义的前提下，
 * 本类仅补齐为当月第一天 {@code 2024-09-01}，从而避免结构化解析因日期格式不完整失败。</p>
 */
final class ResumeDateNormalizer {

    private static final Pattern YEAR_MONTH_DATE_FIELD = Pattern.compile(
            "\\\"(startDate|endDate)\\\"\\s*:\\s*\\\"(\\d{4}-\\d{2})\\\"");

    private ResumeDateNormalizer() {
    }

    static String normalizeYearMonthDates(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        Matcher matcher = YEAR_MONTH_DATE_FIELD.matcher(json);
        StringBuffer normalized = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(normalized,
                    Matcher.quoteReplacement("\"" + matcher.group(1) + "\":\"" + matcher.group(2) + "-01\""));
        }
        matcher.appendTail(normalized);
        return normalized.toString();
    }
}
