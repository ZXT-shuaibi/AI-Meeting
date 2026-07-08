package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.hewei.hzyjy.xunzhi.career.agent.cv.CvOptimizationOrchestrator;
import com.hewei.hzyjy.xunzhi.career.agent.cv.CvOptimizationResult;
import com.hewei.hzyjy.xunzhi.career.agent.interview.CareerInterviewExecutionBridge;
import com.hewei.hzyjy.xunzhi.career.agent.interview.InterviewPlanningService;
import com.hewei.hzyjy.xunzhi.career.memory.DecisionIndex;
import com.hewei.hzyjy.xunzhi.career.memory.HybridCompactingChatMemory;
import com.hewei.hzyjy.xunzhi.career.memory.InterviewRuleBasedScorer;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.ProjectBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.SkillBO;
import com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeApplicationServiceTest {

    @Test
    void optimizeDoesNotOverwritePrimaryResumeWhenScoreGateFails() {
        CvBO original = CvBO.builder().id(1L).userId(7L).name("candidate").summary("original").build();
        CvBO draft = original.toBuilder().summary("low score draft").build();
        ResumeStore store = mock(ResumeStore.class);
        when(store.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(original));
        ResumeRagService ragService = mock(ResumeRagService.class);
        when(ragService.retrieveTemplates(any(), eq(3), eq(7L), eq(Set.of("1")))).thenReturn(List.of("template"));
        CvOptimizationOrchestrator orchestrator = mock(CvOptimizationOrchestrator.class);
        when(orchestrator.optimize(eq(original), eq("Java JD"), eq(List.of("template")), eq(3)))
                .thenReturn(CvOptimizationResult.builder()
                        .cv(draft)
                        .iterations(3)
                        .scoreGatePassed(false)
                        .failureReason("Score gate not reached after max iterations")
                        .reviewHistory(List.of())
                        .build());
        ResumeApplicationService service = service(store, ragService, orchestrator);

        CvOptimizationResult result = service.optimize(7L, 1L, "Java JD");

        assertEquals("low score draft", result.cv().getSummary());
        verify(store, never()).save(any());
    }

    @Test
    void uploadRejectsOversizedResumeBeforeReadingBytes() {
        ResumeStore store = mock(ResumeStore.class);
        ResumeRagService ragService = mock(ResumeRagService.class);
        CvOptimizationOrchestrator orchestrator = mock(CvOptimizationOrchestrator.class);
        ResumeApplicationService service = service(store, ragService, orchestrator);
        MockMultipartFile file = new MockMultipartFile(
                "resume",
                "resume.txt",
                "text/plain",
                new byte[5 * 1024 * 1024 + 1]
        );

        assertThrows(IllegalArgumentException.class, () -> service.upload(7L, file));

        verify(store, never()).save(any());
    }

    @Test
    void uploadParsesBoundedDocxContent() throws Exception {
        ResumeStore store = mock(ResumeStore.class);
        ResumeRagService ragService = mock(ResumeRagService.class);
        when(store.save(any())).thenAnswer(invocation -> ((CvBO) invocation.getArgument(0)).toBuilder().id(11L).build());
        when(store.findByIdAndUserId(11L, 7L)).thenReturn(Optional.of(CvBO.builder().id(11L).userId(7L).name("resume.docx").summary("Java Redis").build()));
        when(ragService.storeCvBO(any())).thenReturn(List.of());
        ResumeApplicationService service = service(store, ragService, mock(CvOptimizationOrchestrator.class));
        MockMultipartFile file = new MockMultipartFile(
                "resume",
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                minimalDocx("Java Redis Spring AI")
        );

        ResumeUploadResult result = service.upload(7L, file);

        assertEquals(11L, result.resumeId());
        verify(store).save(any());
    }

    @Test
    void uploadExtractsTextFromPdfResume() throws Exception {
        ResumeStore store = mock(ResumeStore.class);
        ResumeRagService ragService = mock(ResumeRagService.class);
        when(store.save(any())).thenAnswer(invocation -> ((CvBO) invocation.getArgument(0)).toBuilder().id(13L).build());
        when(store.findByIdAndUserId(13L, 7L)).thenReturn(Optional.of(CvBO.builder().id(13L).userId(7L).name("resume.pdf").summary("Java Redis PDF resume").build()));
        when(ragService.storeCvBO(any())).thenReturn(List.of());
        ResumeApplicationService service = service(store, ragService, mock(CvOptimizationOrchestrator.class));
        MockMultipartFile file = new MockMultipartFile(
                "resume",
                "resume.pdf",
                "application/pdf",
                minimalPdf("Java Redis PDF resume")
        );

        ResumeUploadResult result = service.upload(7L, file);

        ArgumentCaptor<CvBO> savedCv = ArgumentCaptor.forClass(CvBO.class);
        assertEquals(13L, result.resumeId());
        verify(store).save(savedCv.capture());
        org.junit.jupiter.api.Assertions.assertTrue(savedCv.getValue().getSummary().contains("Java Redis PDF resume"));
    }

    @Test
    void uploadRejectsPdfResumeWithTooManyPages() throws Exception {
        ResumeStore store = mock(ResumeStore.class);
        ResumeRagService ragService = mock(ResumeRagService.class);
        ResumeApplicationService service = service(store, ragService, mock(CvOptimizationOrchestrator.class));
        MockMultipartFile file = new MockMultipartFile(
                "resume",
                "resume.pdf",
                "application/pdf",
                minimalPdfWithPages(41)
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.upload(7L, file));

        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("PDF resume exceeds 40 page limit"));
        verify(store, never()).save(any());
    }

    @Test
    void uploadAcceptsPdfResumeAtPageLimit() throws Exception {
        ResumeStore store = mock(ResumeStore.class);
        ResumeRagService ragService = mock(ResumeRagService.class);
        when(store.save(any())).thenAnswer(invocation -> ((CvBO) invocation.getArgument(0)).toBuilder().id(14L).build());
        when(store.findByIdAndUserId(14L, 7L)).thenReturn(Optional.of(CvBO.builder().id(14L).userId(7L).name("resume.pdf").summary("Java Redis page limit resume").build()));
        when(ragService.storeCvBO(any())).thenReturn(List.of());
        ResumeApplicationService service = service(store, ragService, mock(CvOptimizationOrchestrator.class));
        MockMultipartFile file = new MockMultipartFile(
                "resume",
                "resume.pdf",
                "application/pdf",
                minimalPdfWithPages(40, "Java Redis page limit resume")
        );

        ResumeUploadResult result = service.upload(7L, file);

        assertEquals(14L, result.resumeId());
        verify(store).save(any());
    }

    @Test
    void uploadUsesStructuredResumeParserBeforeHeuristicFallback() {
        ResumeStore store = mock(ResumeStore.class);
        ResumeRagService ragService = mock(ResumeRagService.class);
        CvBO structured = CvBO.builder()
                .name("candidate-structured")
                .title("Java Backend Engineer")
                .summary("Three years of Spring AI and LangChain4j project experience")
                .skills(List.of(SkillBO.builder().name("LangChain4j").level("experienced").build()))
                .projects(List.of(ProjectBO.builder().name("AI Interview Platform").role("Backend Owner").description("RAG and multi-agent loop").build()))
                .build();
        ResumeStructuringService structuringService = (userId, filename, text) -> structured;
        when(store.save(any())).thenAnswer(invocation -> ((CvBO) invocation.getArgument(0)).toBuilder().id(12L).build());
        when(store.findByIdAndUserId(12L, 7L)).thenAnswer(invocation -> Optional.of(structured.toBuilder().id(12L).userId(7L).build()));
        when(ragService.storeCvBO(any())).thenReturn(List.of());
        ResumeApplicationService service = service(store, ragService, mock(CvOptimizationOrchestrator.class), structuringService);
        MockMultipartFile file = new MockMultipartFile(
                "resume",
                "resume.txt",
                "text/plain",
                "Java Spring AI LangChain4j RAG multi-agent".getBytes(StandardCharsets.UTF_8)
        );

        ResumeUploadResult result = service.upload(7L, file);

        assertEquals("candidate-structured", result.cv().getName());
        assertEquals("AI Interview Platform", result.cv().getProjects().get(0).getName());
        assertEquals("LangChain4j", result.cv().getSkills().get(0).getName());
    }

    private ResumeApplicationService service(ResumeStore store, ResumeRagService ragService, CvOptimizationOrchestrator orchestrator) {
        return service(store, ragService, orchestrator, (userId, filename, text) -> null);
    }

    private ResumeApplicationService service(
            ResumeStore store,
            ResumeRagService ragService,
            CvOptimizationOrchestrator orchestrator,
            ResumeStructuringService structuringService) {
        return new ResumeApplicationService(
                store,
                mock(JobMatchTaskStore.class),
                ragService,
                orchestrator,
                mock(InterviewPlanningService.class),
                mock(CareerInterviewExecutionBridge.class),
                new HybridCompactingChatMemory(request -> null, new InterviewRuleBasedScorer(), new DecisionIndex()),
                structuringService,
                emptyProvider()
        );
    }

    private byte[] minimalDocx(String text) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("<w:document><w:body><w:p><w:r><w:t>" + text + "</w:t></w:r></w:p></w:body></w:document>").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private byte[] minimalPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] minimalPdfWithPages(int pages) throws Exception {
        return minimalPdfWithPages(pages, null);
    }

    private byte[] minimalPdfWithPages(int pages, String firstPageText) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (i == 0 && firstPageText != null) {
                    try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                        contentStream.beginText();
                        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        contentStream.newLineAtOffset(72, 720);
                        contentStream.showText(firstPageText);
                        contentStream.endText();
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return null;
            }

            @Override
            public T getIfAvailable() {
                return null;
            }

            @Override
            public T getIfUnique() {
                return null;
            }

            @Override
            public T getObject() {
                return null;
            }
        };
    }
}
