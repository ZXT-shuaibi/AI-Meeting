package com.hewei.hzyjy.xunzhi.career.resume.render;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayOutputStream;

public class DocxResumeRenderBackend {

    public byte[] toDocx(String markdown, ResumeDocxConfig config) {
        ResumeDocxConfig effectiveConfig = config == null ? ResumeDocxConfig.defaults() : config;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String line : ResumeMarkdownText.printableLines(markdown)) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setFontFamily(effectiveConfig.fontFamily());
                run.setText(ResumeMarkdownText.stripMarkdown(line));
                if (line.startsWith("# ")) {
                    run.setBold(true);
                    run.setFontSize(18);
                } else if (line.startsWith("## ")) {
                    run.setBold(true);
                    run.setFontSize(14);
                } else if (line.startsWith("### ")) {
                    run.setBold(true);
                    run.setFontSize(12);
                } else {
                    run.setFontSize(10);
                }
            }
            document.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new ResumeRenderException("Resume DOCX render failed: " + ex.getMessage(), ex);
        }
    }
}
