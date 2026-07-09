# Rendering And PDF Notes

## JobSpark Reference

JobSpark's rendering pipeline was:

`CvBO -> FreeMarker Markdown -> CommonMark HTML -> openhtmltopdf PDF / docx4j DOCX`

Important design points:

- Templates are the primary presentation layer.
- Markdown is the canonical intermediate format.
- HTML/CSS provides layout and style.
- PDF and DOCX are export backends.
- Font registration and CJK support are critical for Chinese resumes.

## AI-Meeting Fusion

AI-Meeting's current pipeline is:

`CvBO -> FreeMarker Markdown -> CommonMark HTML -> PDFBox PDF / Apache POI DOCX`

Implemented classes:

- `CvRendererFacade`
- `ResumeTemplateService`
- `ResumeMarkdownService`
- `ResumeHtmlService`
- `PdfResumeRenderBackend`
- `DocxResumeRenderBackend`
- `ResumeRenderService`

Exposed API:

- `GET /api/xunzhi/v1/resumes/{resumeId}/render/markdown`
- `GET /api/xunzhi/v1/resumes/{resumeId}/render/html`
- `GET /api/xunzhi/v1/resumes/{resumeId}/render/pdf`
- `GET /api/xunzhi/v1/resumes/{resumeId}/render/docx`

## PDF Processing Choice

For text extraction, AI-Meeting follows the modern PDFBox 3.x pattern:

- `Loader.loadPDF(RandomAccessReadBuffer)`
- bounded pages
- bounded extracted text

This avoids relying on deprecated PDF loading APIs and reduces memory risk for larger PDF resumes.

For PDF export, AI-Meeting currently uses PDFBox directly instead of openhtmltopdf. This is lower fidelity for complex CSS but keeps dependency risk lower and avoids adding a second PDF layout engine before the core migration stabilizes.

## Future High-Fidelity Backend

If visual parity becomes important, add an optional backend behind the same facade:

- `OpenHtmlToPdfResumeRenderBackend`
- configurable font directory
- strict page-break CSS
- template validation
- snapshot tests comparing expected rendered text and file size range

Do not remove the PDFBox backend; keep it as a reliable fallback.
