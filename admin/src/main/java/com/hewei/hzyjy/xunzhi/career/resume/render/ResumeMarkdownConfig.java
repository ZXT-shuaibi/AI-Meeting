package com.hewei.hzyjy.xunzhi.career.resume.render;

public record ResumeMarkdownConfig(
        String format,
        int headingOffset,
        boolean compactList,
        boolean includeHeaderBlock
) {
    public static ResumeMarkdownConfig defaults() {
        return new ResumeMarkdownConfig("markdown", 0, true, true);
    }
}
