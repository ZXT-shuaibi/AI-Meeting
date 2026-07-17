package com.hewei.hzyjy.xunzhi.career.resume.application;

@FunctionalInterface
public interface ResumeOcrFallback {

    String extract(byte[] pdfBytes, int pageCount) throws Exception;

    static ResumeOcrFallback disabled() {
        return (pdfBytes, pageCount) -> "";
    }
}
