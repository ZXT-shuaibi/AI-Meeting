package com.hewei.hzyjy.xunzhi.career.resume.render;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class PdfResumeRenderBackend {

    private static final String PDF_FONT_RESOURCE = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";

    public byte[] toPdf(String markdown, ResumePdfConfig config) {
        List<String> lines = ResumeMarkdownText.printableLines(markdown);
        ResumePdfConfig effectiveConfig = config == null ? ResumePdfConfig.defaults() : config;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfFont font = loadPdfFont(document, effectiveConfig.fontCandidates());
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
            throw new ResumeRenderException("Resume PDF render failed: " + ex.getMessage(), ex);
        }
    }

    static List<String> defaultFontCandidates() {
        return List.of(
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/Deng.ttf",
                "C:/Windows/Fonts/NotoSansSC-VF.ttf",
                "C:/Windows/Fonts/msyh.ttf",
                "C:/Windows/Fonts/simsun.ttc",
                "C:/Windows/Fonts/msyh.ttc",
                "/System/Library/Fonts/PingFang.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJKsc-Regular.otf"
        );
    }

    private PdfFont loadPdfFont(PDDocument document, List<String> fontCandidates) throws java.io.IOException {
        List<String> candidates = fontCandidates == null ? List.of() : fontCandidates;
        for (String candidate : candidates) {
            File fontFile = new File(candidate);
            if (!fontFile.isFile()) {
                continue;
            }
            if (candidate.toLowerCase().endsWith(".ttc")) {
                PdfFont font = loadTtcFont(document, fontFile);
                if (font != null) {
                    return font;
                }
            } else {
                try {
                    return new PdfFont(PDType0Font.load(document, fontFile), true);
                } catch (Exception ignored) {
                    // Keep delivery available even when a configured system font is unreadable.
                }
            }
        }
        try (InputStream input = PdfResumeRenderBackend.class.getResourceAsStream(PDF_FONT_RESOURCE)) {
            if (input == null) {
                throw new ResumeRenderException("PDF font resource not found: " + PDF_FONT_RESOURCE);
            }
            return new PdfFont(PDType0Font.load(document, input), false);
        }
    }

    private PdfFont loadTtcFont(PDDocument document, File fontFile) {
        List<TrueTypeFont> openedFonts = new ArrayList<>();
        try (TrueTypeCollection collection = new TrueTypeCollection(fontFile)) {
            final TrueTypeFont[] selected = new TrueTypeFont[1];
            collection.processAllFonts(font -> {
                openedFonts.add(font);
                if (selected[0] == null && supportsChinese(font)) {
                    selected[0] = font;
                }
            });
            if (selected[0] == null && !openedFonts.isEmpty()) {
                selected[0] = openedFonts.get(0);
            }
            if (selected[0] == null) {
                return null;
            }
            PDType0Font loaded = PDType0Font.load(document, selected[0], true);
            openedFonts.remove(selected[0]);
            return new PdfFont(loaded, true);
        } catch (Exception ignored) {
            return null;
        } finally {
            for (TrueTypeFont font : openedFonts) {
                try {
                    font.close();
                } catch (Exception ignored) {
                    // Ignore cleanup errors for fallback font probing.
                }
            }
        }
    }

    private boolean supportsChinese(TrueTypeFont font) {
        try {
            return font.getUnicodeCmapLookup().getGlyphId('中') != 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private String toPdfSafeLine(String line, PdfFont font) {
        String stripped = ResumeMarkdownText.stripMarkdown(line).replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ");
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
        return result.isBlank() ? " " : result;
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
