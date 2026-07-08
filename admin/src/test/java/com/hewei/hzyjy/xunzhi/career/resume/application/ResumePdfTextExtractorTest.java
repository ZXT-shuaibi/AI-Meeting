package com.hewei.hzyjy.xunzhi.career.resume.application;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumePdfTextExtractorTest {

    private final ResumePdfTextExtractor extractor = new ResumePdfTextExtractor();

    @Test
    void extractsTextFromPdfUsingModernPdfboxPath() throws Exception {
        String text = extractor.extract(new ByteArrayInputStream(pdfWithPages(1, "Java Redis PDF resume", 1)));

        assertTrue(text.contains("Java Redis PDF resume"));
    }

    @Test
    void rejectsPdfWhenPageCountExceedsLimit() throws Exception {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> extractor.extract(new ByteArrayInputStream(pdfWithPages(41, "too many pages", 1))));

        assertTrue(ex.getMessage().contains("PDF resume exceeds 40 page limit"));
    }

    @Test
    void capsExtractedTextToResumeTextLimit() throws Exception {
        String text = extractor.extract(new ByteArrayInputStream(pdfWithPages(40, repeat("Java backend engineer ", 4), 90)));

        assertEquals(120000, text.length());
    }

    private byte[] pdfWithPages(int pages, String line, int linesPerPage) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    contentStream.newLineAtOffset(36, 760);
                    for (int j = 0; j < linesPerPage; j++) {
                        contentStream.showText(line);
                        contentStream.newLineAtOffset(0, -10);
                    }
                    contentStream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private String repeat(String value, int times) {
        return value.repeat(times);
    }
}
