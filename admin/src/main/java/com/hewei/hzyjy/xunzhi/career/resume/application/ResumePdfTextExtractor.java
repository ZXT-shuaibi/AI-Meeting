package com.hewei.hzyjy.xunzhi.career.resume.application;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.Writer;

@Slf4j
@Component
public class ResumePdfTextExtractor {

    static final int MAX_RESUME_TEXT_LENGTH = 120000;
    static final int MAX_PDF_PAGES = 40;

    public String extract(InputStream input) throws Exception {
        try (RandomAccessReadBuffer buffer = new RandomAccessReadBuffer(input);
             PDDocument document = Loader.loadPDF(buffer)) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("Encrypted PDF resumes are not supported");
            }
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new IllegalArgumentException("PDF resume exceeds " + MAX_PDF_PAGES + " page limit");
            }
            PDFTextStripper pdfStripper = new PDFTextStripper();
            pdfStripper.setSortByPosition(true);
            CappedResumeTextWriter writer = new CappedResumeTextWriter(MAX_RESUME_TEXT_LENGTH);
            try {
                pdfStripper.writeText(document, writer);
            } catch (ResumeTextLimitReachedException ignored) {
                log.debug("PDF resume text extraction stopped after reaching {} characters", MAX_RESUME_TEXT_LENGTH);
            }
            return writer.normalizedText();
        }
    }

    private static final class CappedResumeTextWriter extends Writer {
        private final StringBuilder buffer;
        private final int maxChars;
        private boolean previousWhitespace;

        private CappedResumeTextWriter(int maxChars) {
            this.maxChars = maxChars;
            this.buffer = new StringBuilder(Math.min(maxChars, 8192));
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            if (len <= 0) {
                return;
            }
            for (int i = off; i < off + len; i++) {
                char next = cbuf[i];
                if (Character.isWhitespace(next)) {
                    if (buffer.isEmpty() || previousWhitespace) {
                        continue;
                    }
                    next = ' ';
                    previousWhitespace = true;
                } else {
                    previousWhitespace = false;
                }
                if (buffer.length() >= maxChars) {
                    throw new ResumeTextLimitReachedException();
                }
                buffer.append(next);
                if (buffer.length() >= maxChars) {
                    throw new ResumeTextLimitReachedException();
                }
            }
        }

        @Override
        public void flush() {
            // No external resource to flush.
        }

        @Override
        public void close() {
            // No external resource to close.
        }

        private String normalizedText() {
            int length = buffer.length();
            if (length > 0 && buffer.charAt(length - 1) == ' ') {
                return buffer.substring(0, length - 1);
            }
            return buffer.toString();
        }
    }

    private static final class ResumeTextLimitReachedException extends RuntimeException {
    }
}
