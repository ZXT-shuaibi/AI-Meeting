package com.hewei.hzyjy.xunzhi.career.resume.render;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;

public class CvRendererFacade {

    private final ResumeTemplateService templateService;
    private final ResumeMarkdownService markdownService;
    private final PdfResumeRenderBackend pdfBackend;
    private final DocxResumeRenderBackend docxBackend;
    private final ResumeTemplateFieldMapper fieldMapper;

    public CvRendererFacade() {
        this(new ResumeTemplateService(),
                new ResumeMarkdownService(),
                new PdfResumeRenderBackend(),
                new DocxResumeRenderBackend(),
                new ResumeTemplateFieldMapper());
    }

    CvRendererFacade(
            ResumeTemplateService templateService,
            ResumeMarkdownService markdownService,
            PdfResumeRenderBackend pdfBackend,
            DocxResumeRenderBackend docxBackend,
            ResumeTemplateFieldMapper fieldMapper) {
        this.templateService = templateService;
        this.markdownService = markdownService;
        this.pdfBackend = pdfBackend;
        this.docxBackend = docxBackend;
        this.fieldMapper = fieldMapper;
    }

    public ResumeRenderBundle render(CvBO cv) {
        String markdown = toMarkdown(cv);
        String html = toHtmlFromMarkdown(markdown);
        return new ResumeRenderBundle(
                ResumeRenderArtifact.text("markdown", filename(cv, "md"), "text/markdown;charset=UTF-8", markdown),
                ResumeRenderArtifact.text("html", filename(cv, "html"), "text/html;charset=UTF-8", html),
                ResumeRenderArtifact.binary("pdf", filename(cv, "pdf"), "application/pdf", toPdf(markdown)),
                ResumeRenderArtifact.binary("docx", filename(cv, "docx"), "application/vnd.openxmlformats-officedocument.wordprocessingml.document", toDocx(markdown))
        );
    }

    public String toMarkdown(CvBO cv) {
        return toMarkdown(cv, ResumeMarkdownConfig.defaults());
    }

    public String toMarkdown(CvBO cv, ResumeMarkdownConfig config) {
        return ResumeMarkdownText.limit(templateService.renderMarkdown(cv, config, fieldMapper));
    }

    public String toHtmlFromMarkdown(String markdown) {
        return toHtmlFromMarkdown(markdown, ResumeHtmlConfig.defaults());
    }

    public String toHtmlFromMarkdown(String markdown, ResumeHtmlConfig config) {
        return markdownService.toHtmlFromMarkdown(ResumeMarkdownText.limit(markdown), config);
    }

    public byte[] toPdf(String markdown) {
        return toPdf(markdown, ResumePdfConfig.defaults());
    }

    public byte[] toPdf(String markdown, ResumePdfConfig config) {
        return pdfBackend.toPdf(ResumeMarkdownText.limit(markdown), config);
    }

    public byte[] toDocx(String markdown) {
        return toDocx(markdown, ResumeDocxConfig.defaults());
    }

    public byte[] toDocx(String markdown, ResumeDocxConfig config) {
        return docxBackend.toDocx(ResumeMarkdownText.limit(markdown), config);
    }

    private String filename(CvBO cv, String extension) {
        String rawName = cv == null || cv.getName() == null || cv.getName().isBlank()
                ? "resume"
                : cv.getName().strip();
        return rawName.replaceAll("[\\\\/:*?\"<>|\\s]+", "-") + "." + extension;
    }
}
