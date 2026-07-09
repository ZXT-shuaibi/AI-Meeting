package com.hewei.hzyjy.xunzhi.career.resume.render;

import java.util.ArrayList;
import java.util.List;

final class ResumeMarkdownText {

    private static final int MAX_RENDER_TEXT_LENGTH = 120000;

    private ResumeMarkdownText() {
    }

    static List<String> printableLines(String markdown) {
        String source = limit(markdown == null ? "" : markdown);
        List<String> lines = new ArrayList<>();
        for (String line : source.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines.isEmpty() ? List.of(" ") : lines;
    }

    static String stripMarkdown(String line) {
        return (line == null ? "" : line)
                .replaceFirst("^#{1,6}\\s+", "")
                .replaceFirst("^-\\s+", "• ")
                .replace("**", "")
                .replaceAll("\\[(.*?)]\\((.*?)\\)", "$1 $2");
    }

    static String limit(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= MAX_RENDER_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_RENDER_TEXT_LENGTH);
    }
}
