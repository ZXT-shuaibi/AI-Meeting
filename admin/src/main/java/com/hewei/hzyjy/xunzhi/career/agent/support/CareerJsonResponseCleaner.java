package com.hewei.hzyjy.xunzhi.career.agent.support;

import com.alibaba.fastjson2.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CareerJsonResponseCleaner {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(?:json)?[ \\t]*\\r?\\n?([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private CareerJsonResponseCleaner() {
    }

    public static String cleanJsonResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw new IllegalArgumentException("Input response is null or empty");
        }
        String cleaned = rawResponse.trim();
        if (isJson(cleaned)) {
            return cleaned;
        }

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(cleaned);
        while (matcher.find()) {
            String result = tryParseRobust(matcher.group(1).trim());
            if (result != null) {
                return result;
            }
        }

        String codeBlock = extractFromCodeBlocksManual(cleaned);
        if (codeBlock != null) {
            return codeBlock;
        }

        List<String> candidates = extractJsonCandidates(cleaned);
        for (int i = candidates.size() - 1; i >= 0; i--) {
            String result = tryParseRobust(candidates.get(i));
            if (result != null && result.startsWith("{")) {
                return result;
            }
        }
        for (int i = candidates.size() - 1; i >= 0; i--) {
            String result = tryParseRobust(candidates.get(i));
            if (result != null) {
                return result;
            }
        }

        String fixed = fixControlChars(cleaned);
        if (isJson(fixed)) {
            return fixed;
        }
        throw new IllegalArgumentException("No parseable JSON object found in model response");
    }

    private static String tryParseRobust(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String trimmed = candidate.trim();
        if (isJson(trimmed)) {
            return trimmed;
        }
        String fixed = fixControlChars(trimmed);
        return isJson(fixed) ? fixed : null;
    }

    private static boolean isJson(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        try {
            JSON.parse(candidate);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String extractFromCodeBlocksManual(String text) {
        int searchFrom = 0;
        while (searchFrom < text.length()) {
            int fenceStart = text.indexOf("```", searchFrom);
            if (fenceStart < 0) {
                return null;
            }
            int contentStart = fenceStart + 3;
            String afterFence = text.substring(contentStart);
            String trimmed = afterFence.replaceFirst("(?i)^json[ \\t]*\\r?\\n?", "");
            contentStart = text.length() - trimmed.length();
            int fenceEnd = text.indexOf("```", contentStart);
            String content = text.substring(contentStart, fenceEnd < 0 ? text.length() : fenceEnd).trim();
            String result = tryParseRobust(content);
            if (result != null) {
                return result;
            }
            searchFrom = fenceEnd < 0 ? text.length() : fenceEnd + 3;
        }
        return null;
    }

    private static List<String> extractJsonCandidates(String text) {
        List<String> candidates = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current != '{' && current != '[') {
                continue;
            }
            char close = current == '{' ? '}' : ']';
            String extracted = extractBracedStructure(text, current, close, i);
            if (extracted != null) {
                candidates.add(extracted);
            }
        }
        return candidates;
    }

    private static String extractBracedStructure(String text, char openBrace, char closeBrace, int start) {
        if (text == null || start < 0 || start >= text.length()) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        char previous = 0;
        for (int i = start; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '"' && previous != '\\') {
                inString = !inString;
            }
            if (!inString) {
                if (current == openBrace) {
                    depth++;
                } else if (current == closeBrace) {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
            }
            previous = current;
        }
        return null;
    }

    private static String fixControlChars(String input) {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        char previous = 0;
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '"' && previous != '\\') {
                inString = !inString;
                result.append(current);
            } else if (inString && current == '\n') {
                result.append("\\n");
            } else if (inString && current == '\r') {
                result.append("\\r");
            } else if (inString && current == '\t') {
                result.append("\\t");
            } else if (!inString && (current == '\n' || current == '\r')) {
                // Ignore structural whitespace outside JSON strings.
            } else {
                result.append(current);
            }
            previous = current;
        }
        return result.toString();
    }
}
