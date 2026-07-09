package com.hewei.hzyjy.xunzhi.career.resume.render;

import com.hewei.hzyjy.xunzhi.career.resume.model.CertificateBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.ContactBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.EducationBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.ExperienceBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.HighlightBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.ProjectBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.SkillBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.SocialLinkBO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class ResumeRenderService {

    private static final int MAX_RENDER_TEXT_LENGTH = 120000;
    private static final String PDF_FONT_RESOURCE = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final List<String> CJK_FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/msyh.ttf",
            "C:/Windows/Fonts/simsun.ttc",
            "C:/Windows/Fonts/msyh.ttc",
            "/System/Library/Fonts/PingFang.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJKsc-Regular.otf"
    );

    private final List<String> fontCandidates;

    public ResumeRenderService() {
        this(CJK_FONT_CANDIDATES);
    }

    ResumeRenderService(List<String> fontCandidates) {
        this.fontCandidates = fontCandidates == null ? List.of() : List.copyOf(fontCandidates);
    }

    public ResumeRenderBundle render(CvBO cv) {
        ResumeRenderArtifact markdown = renderMarkdown(cv);
        ResumeRenderArtifact html = renderHtml(cv);
        return new ResumeRenderBundle(
                markdown,
                html,
                renderPdf(cv),
                renderDocx(cv)
        );
    }

    public ResumeRenderArtifact renderMarkdown(CvBO cv) {
        return ResumeRenderArtifact.text("markdown", filename(cv, "md"), "text/markdown;charset=UTF-8", toMarkdown(cv));
    }

    public ResumeRenderArtifact renderHtml(CvBO cv) {
        String markdown = toMarkdown(cv);
        return ResumeRenderArtifact.text("html", filename(cv, "html"), "text/html;charset=UTF-8", toHtml(markdown));
    }

    public ResumeRenderArtifact renderPdf(CvBO cv) {
        return ResumeRenderArtifact.binary("pdf", filename(cv, "pdf"), "application/pdf", toPdf(toMarkdown(cv)));
    }

    public ResumeRenderArtifact renderDocx(CvBO cv) {
        return ResumeRenderArtifact.binary("docx", filename(cv, "docx"), "application/vnd.openxmlformats-officedocument.wordprocessingml.document", toDocx(toMarkdown(cv)));
    }

    public String toMarkdown(CvBO cv) {
        if (cv == null) {
            throw new IllegalArgumentException("CvBO must not be null");
        }
        StringBuilder markdown = new StringBuilder();
        heading(markdown, 1, value(cv.getName(), "未命名简历"));
        line(markdown, bold("目标岗位") + "：" + value(cv.getTitle(), "未填写"));
        appendContact(markdown, cv.getContact());
        section(markdown, "个人摘要", cv.getSummary());
        appendSkills(markdown, cv.getSkills());
        appendProjects(markdown, cv.getProjects());
        appendExperiences(markdown, cv.getExperiences());
        appendEducations(markdown, cv.getEducations());
        appendSocialLinks(markdown, cv.getSocialLinks());
        appendCertificates(markdown, cv.getCertificates());
        return limit(markdown.toString().trim());
    }

    public String toHtml(String markdown) {
        String body = markdownToHtml(markdown == null ? "" : markdown);
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8"></meta>
                  <style>
                    body { font-family: "Noto Sans SC", "Microsoft YaHei", sans-serif; line-height: 1.62; color: #1f2937; margin: 32px; }
                    h1 { font-size: 28px; margin-bottom: 6px; }
                    h2 { font-size: 18px; border-bottom: 1px solid #d1d5db; padding-bottom: 4px; margin-top: 22px; }
                    h3 { font-size: 15px; margin: 14px 0 4px; }
                    ul { margin: 6px 0 12px 20px; padding: 0; }
                    li { margin: 3px 0; }
                    p { margin: 5px 0; }
                    strong { color: #111827; }
                  </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(body);
    }

    public byte[] toPdf(String markdown) {
        List<String> lines = printableLines(markdown);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfFont font = loadPdfFont(document);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            float margin = 42;
            float y = page.getMediaBox().getHeight() - margin;
            stream.beginText();
            stream.setFont(font.font(), 11);
            stream.newLineAtOffset(margin, y);
            for (String line : lines) {
                if (y < margin + 20) {
                    stream.endText();
                    stream.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    y = page.getMediaBox().getHeight() - margin;
                    stream.beginText();
                    stream.setFont(font.font(), 11);
                    stream.newLineAtOffset(margin, y);
                }
                stream.showText(toPdfSafeLine(line, font));
                stream.newLineAtOffset(0, -15);
                y -= 15;
            }
            stream.endText();
            stream.close();
            document.save(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Resume PDF render failed: " + ex.getMessage(), ex);
        }
    }

    public byte[] toDocx(String markdown) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String line : printableLines(markdown)) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(stripMarkdown(line));
                if (line.startsWith("# ")) {
                    run.setBold(true);
                    run.setFontSize(18);
                } else if (line.startsWith("## ")) {
                    run.setBold(true);
                    run.setFontSize(14);
                } else {
                    run.setFontSize(10);
                }
            }
            document.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Resume DOCX render failed: " + ex.getMessage(), ex);
        }
    }

    private void appendContact(StringBuilder markdown, ContactBO contact) {
        if (contact == null) {
            return;
        }
        List<String> parts = new ArrayList<>();
        addPart(parts, contact.getPhone());
        addPart(parts, contact.getEmail());
        addPart(parts, contact.getWebsite());
        addPart(parts, contact.getLocation());
        if (!parts.isEmpty()) {
            line(markdown, bold("联系方式") + "：" + String.join(" / ", parts));
        }
    }

    private void appendSkills(StringBuilder markdown, List<SkillBO> skills) {
        if (skills == null || skills.isEmpty()) {
            return;
        }
        heading(markdown, 2, "技能清单");
        for (SkillBO skill : skills) {
            bullet(markdown, value(skill.getName(), "未命名技能") + (StringUtils.hasText(skill.getLevel()) ? "（" + skill.getLevel() + "）" : ""));
        }
    }

    private void appendProjects(StringBuilder markdown, List<ProjectBO> projects) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        heading(markdown, 2, "项目经历");
        for (ProjectBO project : projects) {
            heading(markdown, 3, value(project.getName(), "未命名项目") + optionalRole(project.getRole()));
            section(markdown, null, project.getDescription());
            appendHighlights(markdown, project.getHighlights());
        }
    }

    private void appendExperiences(StringBuilder markdown, List<ExperienceBO> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return;
        }
        heading(markdown, 2, "工作经历");
        for (ExperienceBO experience : experiences) {
            heading(markdown, 3, value(experience.getCompany(), "未命名公司") + optionalRole(experience.getRole()));
            section(markdown, null, experience.getDescription());
            appendHighlights(markdown, experience.getHighlights());
        }
    }

    private void appendEducations(StringBuilder markdown, List<EducationBO> educations) {
        if (educations == null || educations.isEmpty()) {
            return;
        }
        heading(markdown, 2, "教育经历");
        for (EducationBO education : educations) {
            bullet(markdown, value(education.getSchool(), "未命名学校")
                    + " / " + value(education.getMajor(), "未填写专业")
                    + " / " + value(education.getDegree(), "未填写学历")
                    + dateRange(education.getStartDate(), education.getEndDate()));
        }
    }

    private void appendSocialLinks(StringBuilder markdown, List<SocialLinkBO> links) {
        if (links == null || links.isEmpty()) {
            return;
        }
        heading(markdown, 2, "社交链接");
        for (SocialLinkBO link : links) {
            bullet(markdown, value(link.getName(), "链接") + "：" + value(link.getUrl(), ""));
        }
    }

    private void appendCertificates(StringBuilder markdown, List<CertificateBO> certificates) {
        if (certificates == null || certificates.isEmpty()) {
            return;
        }
        heading(markdown, 2, "证书");
        for (CertificateBO certificate : certificates) {
            bullet(markdown, value(certificate.getName(), "未命名证书") + " / " + value(certificate.getIssuer(), "未知机构"));
        }
    }

    private void appendHighlights(StringBuilder markdown, List<HighlightBO> highlights) {
        if (highlights == null) {
            return;
        }
        for (HighlightBO highlight : highlights) {
            if (highlight != null && StringUtils.hasText(highlight.getHighlight())) {
                bullet(markdown, highlight.getHighlight());
            }
        }
    }

    private void section(StringBuilder markdown, String title, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (StringUtils.hasText(title)) {
            heading(markdown, 2, title);
        }
        line(markdown, content);
    }

    private String markdownToHtml(String markdown) {
        StringBuilder html = new StringBuilder();
        boolean inList = false;
        for (String rawLine : printableLines(markdown)) {
            String line = rawLine.trim();
            if (line.startsWith("- ")) {
                if (!inList) {
                    html.append("<ul>\n");
                    inList = true;
                }
                html.append("<li>").append(inlineHtml(line.substring(2))).append("</li>\n");
                continue;
            }
            if (inList) {
                html.append("</ul>\n");
                inList = false;
            }
            if (line.startsWith("### ")) {
                html.append("<h3>").append(inlineHtml(line.substring(4))).append("</h3>\n");
            } else if (line.startsWith("## ")) {
                html.append("<h2>").append(inlineHtml(line.substring(3))).append("</h2>\n");
            } else if (line.startsWith("# ")) {
                html.append("<h1>").append(inlineHtml(line.substring(2))).append("</h1>\n");
            } else if (!line.isBlank()) {
                html.append("<p>").append(inlineHtml(line)).append("</p>\n");
            }
        }
        if (inList) {
            html.append("</ul>\n");
        }
        return html.toString();
    }

    private String inlineHtml(String value) {
        String escaped = Jsoup.parse(value == null ? "" : value).text();
        return escaped.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
    }

    private List<String> printableLines(String markdown) {
        String source = limit(markdown == null ? "" : markdown);
        List<String> lines = new ArrayList<>();
        for (String line : source.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines.isEmpty() ? List.of(" ") : lines;
    }

    private PdfFont loadPdfFont(PDDocument document) throws java.io.IOException {
        for (String candidate : fontCandidates) {
            File fontFile = new File(candidate);
            if (fontFile.isFile() && !candidate.toLowerCase().endsWith(".ttc")) {
                try {
                    return new PdfFont(PDType0Font.load(document, fontFile), true);
                } catch (Exception ignored) {
                    // Keep delivery available even when a configured system font is unreadable.
                }
            }
        }
        try (InputStream input = ResumeRenderService.class.getResourceAsStream(PDF_FONT_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("PDF font resource not found: " + PDF_FONT_RESOURCE);
            }
            return new PdfFont(PDType0Font.load(document, input), false);
        }
    }

    private String toPdfSafeLine(String line, PdfFont font) {
        String stripped = stripMarkdown(line).replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ");
        StringBuilder safe = new StringBuilder(stripped.length());
        stripped.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            if (font.canEncode(character)) {
                safe.append(character);
            } else if (codePoint == '\t') {
                safe.append(' ');
            } else if (font.cjkCapable() && codePoint > 0x7F) {
                safe.append(' ');
            } else {
                safe.append(codePoint >= 0x20 && codePoint <= 0x7E ? character : " ");
            }
        });
        String result = safe.toString().stripTrailing();
        if (result.isBlank()) {
            return " ";
        }
        return result;
    }

    private String stripMarkdown(String line) {
        return (line == null ? "" : line)
                .replaceFirst("^#{1,6}\\s+", "")
                .replaceFirst("^-\\s+", "• ")
                .replace("**", "");
    }

    private String filename(CvBO cv, String extension) {
        String name = value(cv == null ? null : cv.getName(), "resume")
                .replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
        return name + "." + extension;
    }

    private String optionalRole(String role) {
        return StringUtils.hasText(role) ? "｜" + role : "";
    }

    private String dateRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return "";
        }
        return "（" + (start == null ? "" : start) + " - " + (end == null ? "至今" : end) + "）";
    }

    private void heading(StringBuilder markdown, int level, String text) {
        line(markdown, "#".repeat(Math.max(1, Math.min(level, 6))) + " " + value(text, ""));
    }

    private void bullet(StringBuilder markdown, String text) {
        line(markdown, "- " + value(text, ""));
    }

    private void line(StringBuilder markdown, String text) {
        if (StringUtils.hasText(text)) {
            markdown.append(text.strip()).append("\n\n");
        }
    }

    private String bold(String text) {
        return "**" + text + "**";
    }

    private void addPart(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value.strip());
        }
    }

    private String value(String value, String fallback) {
        return StringUtils.hasText(value) ? value.strip() : fallback;
    }

    private String limit(String value) {
        if (value == null || value.length() <= MAX_RENDER_TEXT_LENGTH) {
            return value == null ? "" : value;
        }
        return value.substring(0, MAX_RENDER_TEXT_LENGTH);
    }

    private record PdfFont(PDType0Font font, boolean cjkCapable) {
        private boolean canEncode(String character) {
            try {
                font.encode(character);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}
