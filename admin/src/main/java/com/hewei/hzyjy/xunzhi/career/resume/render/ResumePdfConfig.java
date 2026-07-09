package com.hewei.hzyjy.xunzhi.career.resume.render;

import java.util.List;

public record ResumePdfConfig(List<String> fontCandidates) {
    public static ResumePdfConfig defaults() {
        return new ResumePdfConfig(PdfResumeRenderBackend.defaultFontCandidates());
    }
}
