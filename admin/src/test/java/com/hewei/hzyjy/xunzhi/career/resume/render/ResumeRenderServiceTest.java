package com.hewei.hzyjy.xunzhi.career.resume.render;

import com.hewei.hzyjy.xunzhi.career.resume.model.ContactBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.HighlightBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.ProjectBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.SkillBO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeRenderServiceTest {

    private final ResumeRenderService renderService = new ResumeRenderService();

    @TempDir
    Path tempDir;

    @Test
    void rendersResumeToMarkdownHtmlPdfAndDocx() throws Exception {
        CvBO cv = sampleCv();

        ResumeRenderBundle bundle = renderService.render(cv);

        assertEquals("markdown", bundle.markdown().format());
        assertTrue(bundle.markdown().content().contains("# 张三"));
        assertTrue(bundle.markdown().content().contains("AI-Meeting 融合项目"));
        assertTrue(bundle.markdown().content().contains("## 项目经验"));
        assertTrue(bundle.markdown().content().contains("## 技能与亮点"));
        assertEquals("html", bundle.html().format());
        assertTrue(bundle.html().content().contains("<!DOCTYPE html>"));
        assertTrue(bundle.html().content().contains("body class=\"resume-body"));
        assertTrue(bundle.html().content().contains("class=\"container"));
        assertTrue(bundle.html().content().contains("@page"));
        assertTrue(bundle.html().content().contains("AI-Meeting 融合项目"));
        assertEquals("pdf", bundle.pdf().format());
        assertTrue(bundle.pdf().bytes().length > 0);
        assertEquals("docx", bundle.docx().format());
        assertTrue(bundle.docx().bytes().length > 0);

        assertTrue(pdfText(bundle.pdf().bytes()).contains("AI-Meeting"));
        assertTrue(docxText(bundle.docx().bytes()).contains("AI-Meeting 融合项目"));
    }

    @Test
    void exposesJobSparkStyleRendererFacadePipeline() {
        CvRendererFacade facade = new CvRendererFacade();

        String markdown = facade.toMarkdown(sampleCv());
        String html = facade.toHtmlFromMarkdown(markdown);

        assertTrue(markdown.contains("## 个人摘要"));
        assertTrue(markdown.contains("## 项目经验"));
        assertTrue(markdown.contains("## 技能与亮点"));
        assertTrue(html.contains("body class=\"resume-body"));
        assertTrue(html.contains("<div class=\"container"));
        assertTrue(html.contains("@page"));
    }

    @Test
    void pdfRenderingFallsBackWhenConfiguredFontCannotBeLoaded() throws Exception {
        Path brokenFont = tempDir.resolve("broken.ttf");
        Files.writeString(brokenFont, "not-a-font");
        ResumeRenderService service = new ResumeRenderService(List.of(brokenFont.toString()));

        byte[] pdf = service.toPdf("# AI-Meeting 融合项目");

        assertTrue(pdf.length > 0);
        assertTrue(pdfText(pdf).contains("AI-Meeting"));
    }

    @Test
    void rendersUnnamedResumeWithStableFallbackTitle() {
        CvBO cv = CvBO.builder()
                .summary("缺少姓名时仍应可导出，避免解析不完整导致交付中断。")
                .build();

        ResumeRenderArtifact markdown = renderService.renderMarkdown(cv);

        assertTrue(markdown.content().contains("# 未命名简历"));
        assertEquals("resume.md", markdown.filename());
    }

    @Test
    void pdfRenderingPreservesChineseWhenCjkFontIsAvailable() throws Exception {
        Path font = firstExistingFont(List.of(
                Path.of("C:/Windows/Fonts/simhei.ttf"),
                Path.of("C:/Windows/Fonts/Deng.ttf"),
                Path.of("C:/Windows/Fonts/NotoSansSC-VF.ttf")
        ));
        ResumeRenderService service = new ResumeRenderService(List.of(font.toString()));

        byte[] pdf = service.toPdf("# 中文简历\n\n## 项目经验\n\n- 融合简历导出链路");

        String text = pdfText(pdf);
        assertTrue(text.contains("中文简历"));
        assertTrue(text.contains("项目经验"));
    }

    private String pdfText(byte[] bytes) throws Exception {
        try (RandomAccessReadBuffer buffer = new RandomAccessReadBuffer(new ByteArrayInputStream(bytes));
             PDDocument document = Loader.loadPDF(buffer)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String docxText(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .reduce("", (left, right) -> left + "\n" + right);
        }
    }

    private Path firstExistingFont(List<Path> candidates) {
        return candidates.stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No CJK test font found"));
    }

    private CvBO sampleCv() {
        return CvBO.builder()
                .id(9L)
                .userId(1001L)
                .name("张三")
                .title("Java 后端工程师")
                .summary("熟悉 Spring AI、LangChain4j、Redis 与 RAG 工程化。")
                .contact(ContactBO.builder().email("zhangsan@example.com").phone("18800001111").build())
                .skills(List.of(
                        SkillBO.builder().name("Spring AI").level("熟练").build(),
                        SkillBO.builder().name("LangChain4j").level("熟练").build()
                ))
                .projects(List.of(ProjectBO.builder()
                        .name("AI-Meeting 融合项目")
                        .role("后端负责人")
                        .description("融合 JobSpark Resume 的 Agent、RAG、Memory 与渲染链路。")
                        .highlights(List.of(HighlightBO.builder().highlight("实现 Plan-Execute-Reflect 面试规划闭环").build()))
                        .build()))
                .build();
    }
}
