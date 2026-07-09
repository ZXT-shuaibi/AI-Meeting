package com.hewei.hzyjy.xunzhi.career.resume.render;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ResumeMarkdownService {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .escapeHtml(true)
            .softbreak("<br/>")
            .build();

    public String toHtmlFromMarkdown(String markdown, ResumeHtmlConfig config) {
        if (markdown == null) {
            throw new ResumeRenderException("Markdown content must not be null");
        }
        ResumeHtmlConfig effectiveConfig = config == null ? ResumeHtmlConfig.defaults() : config;
        Node document = parser.parse(markdown);
        String bodyHtml = renderer.render(document);
        String css = loadCss(effectiveConfig.cssResourcePath());
        String containerClass = effectiveConfig.twoColumnLayout() ? "container two-column" : "container";
        String showAvatarClass = effectiveConfig.showAvatar() ? "show-avatar" : "hide-avatar";
        String showSocialClass = effectiveConfig.showSocial() ? "show-social" : "hide-social";

        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8"></meta>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"></meta>
                  <style>
                %s
                  </style>
                </head>
                <body class="resume-body %s %s">
                  <div class="%s">
                %s
                  </div>
                </body>
                </html>
                """.formatted(indentCss(css), showAvatarClass, showSocialClass, containerClass, bodyHtml);
    }

    private String loadCss(String resourcePath) {
        String path = resourcePath == null || resourcePath.isBlank()
                ? ResumeHtmlConfig.defaults().cssResourcePath()
                : resourcePath;
        try (InputStream input = ResumeMarkdownService.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                return defaultCss();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder css = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    css.append(line).append('\n');
                }
                return css.toString();
            }
        } catch (Exception ex) {
            return defaultCss();
        }
    }

    private String defaultCss() {
        return """
                :root {
                  --font-stack: "Noto Sans SC","PingFang SC","Microsoft YaHei","SimSun",sans-serif;
                }
                @page { size: A4; margin: 20mm 15mm; }
                body.resume-body { font-family: var(--font-stack); color: #222; line-height: 1.5; font-size: 12.5pt; }
                .container { max-width: 800px; margin: 0 auto; }
                .container.two-column { display: grid; grid-template-columns: 2fr 1fr; gap: 16px; }
                h1,h2,h3 { margin: 0.25em 0 0.4em 0; line-height: 1.25; }
                h1 { font-size: 22pt; border-bottom: 2px solid #e5e7eb; padding-bottom: 8px; }
                h2 { font-size: 16pt; border-left: 3px solid #0b5fff; padding-left: 8px; }
                ul { margin: 0.4em 0 0.6em 1.2em; padding: 0; }
                """;
    }

    private String indentCss(String css) {
        StringBuilder indented = new StringBuilder();
        for (String line : css.split("\\R", -1)) {
            indented.append("  ").append(line).append('\n');
        }
        return indented.toString();
    }
}
