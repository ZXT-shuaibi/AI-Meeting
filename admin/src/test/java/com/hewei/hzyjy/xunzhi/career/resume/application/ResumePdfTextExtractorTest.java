package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.hewei.hzyjy.xunzhi.common.config.xunfei.XunfeiLatProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

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

    @Test
    void fallsBackToOcrWhenPdfHasNoTextLayer() throws Exception {
        ResumePdfTextExtractor ocrEnabledExtractor = new ResumePdfTextExtractor((pdfBytes, pageCount) -> "OCR resume text");

        String text = ocrEnabledExtractor.extract(new ByteArrayInputStream(imageOnlyPdf()));

        assertEquals("OCR resume text", text);
    }

    @Test
    void usesDedicatedPdfOcrCredentialsWhenTheyAreConfigured() {
        ResumeOcrProperties ocrProperties = new ResumeOcrProperties();
        ocrProperties.setAppId("ocr-app-id");
        ocrProperties.setApiSecret("ocr-api-secret");
        XunfeiLatProperties sharedProperties = xunfeiProperties("shared-app-id", "shared-api-key", "shared-api-secret");

        XunfeiResumeOcrFallback fallback = new XunfeiResumeOcrFallback(ocrProperties, sharedProperties, new FakePdfOcrTransport());

        assertEquals("ocr-app-id", fallback.resolvedAppId());
        assertEquals("ocr-api-secret", fallback.resolvedApiSecret());
    }

    @Test
    void fallsBackToSharedXunfeiCredentialsWhenNoOcrCredentialsAreConfigured() {
        XunfeiResumeOcrFallback fallback = new XunfeiResumeOcrFallback(
                new ResumeOcrProperties(), xunfeiProperties("shared-app-id", "shared-api-key", "shared-api-secret"),
                new FakePdfOcrTransport());

        assertEquals("shared-app-id", fallback.resolvedAppId());
        assertEquals("shared-api-secret", fallback.resolvedApiSecret());
    }

    @Test
    void downloadsMarkdownAfterPdfOcrTaskFinishes() throws Exception {
        FakePdfOcrTransport transport = new FakePdfOcrTransport();
        ResumeOcrProperties properties = new ResumeOcrProperties();
        properties.setAppId("ocr-app-id");
        properties.setApiSecret("ocr-api-secret");
        XunfeiResumeOcrFallback fallback = new XunfeiResumeOcrFallback(
                properties, xunfeiProperties("shared-app-id", "shared-api-key", "shared-api-secret"), transport);

        assertEquals("# Java Resume", fallback.extract(new byte[]{1, 2, 3}, 1));
        assertEquals("markdown", transport.exportFormat);
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

    private XunfeiLatProperties xunfeiProperties(String appId, String apiKey, String apiSecret) {
        XunfeiLatProperties properties = new XunfeiLatProperties();
        properties.setAppId(appId);
        properties.setApiKey(apiKey);
        properties.setApiSecret(apiSecret);
        return properties;
    }

    private static final class FakePdfOcrTransport implements XunfeiPdfOcrTransport {
        private String exportFormat;

        @Override
        public String start(String appId, String timestamp, String signature, byte[] pdfBytes, String exportFormat) {
            this.exportFormat = exportFormat;
            return "{\"code\":0,\"data\":{\"taskNo\":\"task-1\"}}";
        }

        @Override
        public String status(String appId, String timestamp, String signature, String taskNo) {
            return "{\"code\":0,\"data\":{\"status\":\"FINISH\",\"downUrl\":\"https://download.example/result.md\"}}";
        }

        @Override
        public byte[] download(String url) {
            return "# Java Resume\n![scan](data:image/jpeg;base64,abcdef)".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private byte[] imageOnlyPdf() throws Exception {
        BufferedImage image = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 120, 40);
        graphics.setColor(Color.BLACK);
        graphics.drawString("Resume", 10, 25);
        graphics.dispose();
        try (ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
             PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", imageBytes);
            PDPage page = new PDPage();
            document.addPage(page);
            PDImageXObject pdfImage = PDImageXObject.createFromByteArray(document, imageBytes.toByteArray(), "resume.png");
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(pdfImage, 36, 700);
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
