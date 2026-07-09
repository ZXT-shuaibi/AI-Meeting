package com.hewei.hzyjy.xunzhi.career.resume.render;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResumeRenderService {

    private final CvRendererFacade rendererFacade;

    public ResumeRenderService() {
        this(new CvRendererFacade());
    }

    ResumeRenderService(List<String> fontCandidates) {
        this(new CvRendererFacade(
                new ResumeTemplateService(),
                new ResumeMarkdownService(),
                new PdfResumeRenderBackend(),
                new DocxResumeRenderBackend(),
                new ResumeTemplateFieldMapper()) {
            @Override
            public byte[] toPdf(String markdown) {
                return toPdf(markdown, new ResumePdfConfig(fontCandidates));
            }
        });
    }

    ResumeRenderService(CvRendererFacade rendererFacade) {
        this.rendererFacade = rendererFacade;
    }

    public ResumeRenderBundle render(CvBO cv) {
        return rendererFacade.render(cv);
    }

    public ResumeRenderArtifact renderMarkdown(CvBO cv) {
        String markdown = toMarkdown(cv);
        return ResumeRenderArtifact.text("markdown", filename(cv, "md"), "text/markdown;charset=UTF-8", markdown);
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
        return rendererFacade.toMarkdown(cv);
    }

    public String toHtml(String markdown) {
        return rendererFacade.toHtmlFromMarkdown(markdown);
    }

    public byte[] toPdf(String markdown) {
        return rendererFacade.toPdf(markdown);
    }

    public byte[] toDocx(String markdown) {
        return rendererFacade.toDocx(markdown);
    }

    private String filename(CvBO cv, String extension) {
        String rawName = cv == null || cv.getName() == null || cv.getName().isBlank()
                ? "resume"
                : cv.getName().strip();
        return rawName.replaceAll("[\\\\/:*?\"<>|\\s]+", "-") + "." + extension;
    }
}
