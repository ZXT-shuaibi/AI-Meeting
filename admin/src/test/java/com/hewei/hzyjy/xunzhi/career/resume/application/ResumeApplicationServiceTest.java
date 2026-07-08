package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.hewei.hzyjy.xunzhi.career.agent.cv.CvOptimizationOrchestrator;
import com.hewei.hzyjy.xunzhi.career.agent.cv.CvOptimizationResult;
import com.hewei.hzyjy.xunzhi.career.agent.interview.CareerInterviewExecutionBridge;
import com.hewei.hzyjy.xunzhi.career.agent.interview.InterviewPlanningService;
import com.hewei.hzyjy.xunzhi.career.memory.DecisionIndex;
import com.hewei.hzyjy.xunzhi.career.memory.HybridCompactingChatMemory;
import com.hewei.hzyjy.xunzhi.career.memory.InterviewRuleBasedScorer;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagService;
import org.junit.jupiter.api.Test;
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

        org.junit.jupiter.api.Assertions.assertEquals("low score draft", result.cv().getSummary());
        verify(store, never()).save(any());
    }

    @Test
    void uploadRejectsOversizedResumeBeforeReadingBytes() throws Exception {
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

    private ResumeApplicationService service(ResumeStore store, ResumeRagService ragService, CvOptimizationOrchestrator orchestrator) {
        return new ResumeApplicationService(
                store,
                mock(JobMatchTaskStore.class),
                ragService,
                orchestrator,
                mock(InterviewPlanningService.class),
                mock(CareerInterviewExecutionBridge.class),
                new HybridCompactingChatMemory(request -> null, new InterviewRuleBasedScorer(), new DecisionIndex()),
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