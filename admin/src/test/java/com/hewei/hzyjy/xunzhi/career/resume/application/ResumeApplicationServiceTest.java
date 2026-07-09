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
import com.hewei.hzyjy.xunzhi.career.resume.render.ResumeRenderArtifact;
import com.hewei.hzyjy.xunzhi.career.resume.render.ResumeRenderService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;
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

    @Test
    void renderResumeReturnsOwnedArtifactByFormat() {
        CvBO cv = CvBO.builder()
                .id(21L)
                .userId(7L)
                .name("candidate")
                .title("Java Backend Engineer")
                .summary("AI-Meeting JobSpark fusion")
                .build();
        ResumeStore store = mock(ResumeStore.class);
        when(store.findByIdAndUserId(21L, 7L)).thenReturn(Optional.of(cv));
        ResumeRenderService renderService = mock(ResumeRenderService.class);
        when(renderService.renderMarkdown(cv)).thenReturn(new ResumeRenderArtifact(
                "markdown",
                "candidate.md",
                "text/markdown;charset=UTF-8",
                "# candidate\n\nAI-Meeting JobSpark fusion",
                "# candidate\n\nAI-Meeting JobSpark fusion".getBytes(StandardCharsets.UTF_8)
        ));
        ResumeApplicationService service = service(store, mock(ResumeRagService.class), mock(CvOptimizationOrchestrator.class), renderService);

        ResumeRenderArtifact artifact = service.renderResume(7L, 21L, "markdown");

        assertEquals("markdown", artifact.format());
        org.junit.jupiter.api.Assertions.assertTrue(artifact.filename().endsWith(".md"));
        org.junit.jupiter.api.Assertions.assertTrue(artifact.content().contains("AI-Meeting JobSpark fusion"));
        verify(store).findByIdAndUserId(21L, 7L);
        verify(renderService).renderMarkdown(cv);
        verify(renderService, never()).render(any());
    }

    @Test
    void renderResumeAndStoreWritesArtifactToObjectStorage() {
        CvBO cv = CvBO.builder()
                .id(22L)
                .userId(7L)
                .name("candidate")
                .summary("AI-Meeting JobSpark fusion")
                .build();
        ResumeStore store = mock(ResumeStore.class);
        when(store.findByIdAndUserId(22L, 7L)).thenReturn(Optional.of(cv));
        ResumeRenderService renderService = mock(ResumeRenderService.class);
        when(renderService.renderPdf(cv)).thenReturn(new ResumeRenderArtifact(
                "pdf",
                "candidate.pdf",
                "application/pdf",
                "",
                "PDF-BYTES".getBytes(StandardCharsets.UTF_8)
        ));
        InMemoryResumeObjectStorage objectStorage = new InMemoryResumeObjectStorage();
        ResumeApplicationService service = service(
                store,
                mock(ResumeRagService.class),
                mock(CvOptimizationOrchestrator.class),
                (userId, filename, text) -> null,
                renderService,
                new InMemoryResumeParseTaskStore(),
                Runnable::run,
                objectStorage
        );

        ResumeRenderStorageResult result = service.renderResumeAndStore(7L, 22L, "pdf");

        assertEquals("pdf", result.artifact().format());
        assertEquals("memory-object-storage", result.storage().provider());
        org.junit.jupiter.api.Assertions.assertTrue(result.storage().key().contains("career/resume/render/7/22/"));
        org.junit.jupiter.api.Assertions.assertTrue(objectStorage.contains(result.storage().key()));
        verify(renderService).renderPdf(cv);
    }

    @Test
    void renderResumeRejectsUnsupportedFormat() {
        ResumeStore store = mock(ResumeStore.class);
        when(store.findByIdAndUserId(21L, 7L)).thenReturn(Optional.of(CvBO.builder()
                .id(21L)
                .userId(7L)
                .name("candidate")
                .summary("AI-Meeting")
                .build()));
        ResumeRenderService renderService = mock(ResumeRenderService.class);
        ResumeApplicationService service = service(store, mock(ResumeRagService.class), mock(CvOptimizationOrchestrator.class), renderService);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.renderResume(7L, 21L, "xlsx"));

        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("Unsupported resume render format"));
        verify(renderService, never()).render(any());
    }

    @Test
    void uploadAsyncCreatesProcessingTaskAndDefersParsingUntilExecutorRuns() {
        ResumeStore store = mock(ResumeStore.class);
        ResumeRagService ragService = mock(ResumeRagService.class);
        ManualTaskExecutor taskExecutor = new ManualTaskExecutor();
        ResumeParseTaskStore parseTaskStore = new InMemoryResumeParseTaskStore();
        ResumeApplicationService service = service(
                store,
                ragService,
                mock(CvOptimizationOrchestrator.class),
                (userId, filename, text) -> CvBO.builder().name("async-cv").summary(text).build(),
                new ResumeRenderService(),
                parseTaskStore,
                taskExecutor
        );
        MockMultipartFile file = new MockMultipartFile(
                "resume",
                "resume.txt",
                "text/plain",
                "Java Redis async resume".getBytes(StandardCharsets.UTF_8)
        );

        ResumeParseTaskResult created = service.uploadAsync(7L, file, "upload");
        ResumeParseTaskResult beforeRun = service.getParseTask(7L, created.taskId());

        assertEquals(ResumeParseTaskStatus.PROCESSING.name(), beforeRun.status());
        assertEquals(5, beforeRun.progress());
        assertEquals(1, taskExecutor.tasks.size());
        verify(store, never()).save(any());

        when(store.save(any())).thenAnswer(invocation -> ((CvBO) invocation.getArgument(0)).toBuilder().id(31L).userId(7L).build());
        when(store.findByIdAndUserId(31L, 7L)).thenReturn(Optional.of(CvBO.builder().id(31L).userId(7L).name("async-cv").summary("Java Redis async resume").build()));
        when(ragService.storeCvBO(any())).thenReturn(List.of());
        taskExecutor.runNext();

        ResumeParseTaskResult completed = service.getParseTask(7L, created.taskId());
        assertEquals(ResumeParseTaskStatus.COMPLETED.name(), completed.status());
        assertEquals(100, completed.progress());
        assertEquals(31L, completed.resumeId());
        verify(store).save(any());
    }

    @Test
    void uploadAsyncUsesObjectStorageHandoffWhenAvailable() {
        ResumeStore store = mock(ResumeStore.class);
        ResumeRagService ragService = mock(ResumeRagService.class);
        ManualTaskExecutor taskExecutor = new ManualTaskExecutor();
        ResumeParseTaskStore parseTaskStore = new InMemoryResumeParseTaskStore();
        InMemoryResumeObjectStorage objectStorage = new InMemoryResumeObjectStorage();
        ResumeApplicationService service = service(
                store,
                ragService,
                mock(CvOptimizationOrchestrator.class),
                (userId, filename, text) -> CvBO.builder().name("oss-cv").summary(text).build(),
                new ResumeRenderService(),
                parseTaskStore,
                taskExecutor,
                objectStorage
        );
        MockMultipartFile file = new MockMultipartFile(
                "resume",
                "resume.txt",
                "text/plain",
                "Java Redis object storage resume".getBytes(StandardCharsets.UTF_8)
        );

        ResumeParseTaskResult created = service.uploadAsync(7L, file, "upload");

        assertEquals("memory-object-storage", created.storageProvider());
        org.junit.jupiter.api.Assertions.assertTrue(created.storageKey().contains(created.taskId()));
        org.junit.jupiter.api.Assertions.assertTrue(objectStorage.contains(created.storageKey()));
        when(store.save(any())).thenAnswer(invocation -> ((CvBO) invocation.getArgument(0)).toBuilder().id(32L).userId(7L).build());
        when(store.findByIdAndUserId(32L, 7L)).thenReturn(Optional.of(CvBO.builder().id(32L).userId(7L).name("oss-cv").summary("Java Redis object storage resume").build()));
        when(ragService.storeCvBO(any())).thenReturn(List.of());

        taskExecutor.runNext();

        ResumeParseTaskResult completed = service.getParseTask(7L, created.taskId());
        assertEquals(ResumeParseTaskStatus.COMPLETED.name(), completed.status());
        assertEquals(32L, completed.resumeId());
        verify(store).save(any());
    }

    @Test
    void cancelAsyncParseTaskPreventsQueuedExecution() {
        ManualTaskExecutor taskExecutor = new ManualTaskExecutor();
        ResumeStore store = mock(ResumeStore.class);
        ResumeApplicationService service = service(
                store,
                mock(ResumeRagService.class),
                mock(CvOptimizationOrchestrator.class),
                (userId, filename, text) -> CvBO.builder().name("cancelled").summary(text).build(),
                new ResumeRenderService(),
                new InMemoryResumeParseTaskStore(),
                taskExecutor
        );
        MockMultipartFile file = new MockMultipartFile("resume", "resume.txt", "text/plain", "Java".getBytes(StandardCharsets.UTF_8));

        ResumeParseTaskResult created = service.uploadAsync(7L, file, "upload");
        ResumeParseTaskResult canceled = service.cancelParseTask(7L, created.taskId());
        taskExecutor.runNext();
        ResumeParseTaskResult afterWorker = service.getParseTask(7L, created.taskId());

        assertEquals(ResumeParseTaskStatus.CANCELED.name(), canceled.status());
        assertEquals(ResumeParseTaskStatus.CANCELED.name(), afterWorker.status());
        verify(store, never()).save(any());
    }

    @Test
    void retryFailedAsyncParseTaskCreatesNewQueuedTaskFromStoredSnapshot() {
        ResumeStore store = mock(ResumeStore.class);
        ResumeRagService ragService = mock(ResumeRagService.class);
        ManualTaskExecutor taskExecutor = new ManualTaskExecutor();
        ResumeParseTaskStore parseTaskStore = new InMemoryResumeParseTaskStore();
        ResumeApplicationService service = service(
                store,
                ragService,
                mock(CvOptimizationOrchestrator.class),
                (userId, filename, text) -> {
                    throw new IllegalStateException("parser down");
                },
                new ResumeRenderService(),
                parseTaskStore,
                taskExecutor
        );
        MockMultipartFile file = new MockMultipartFile("resume", "resume.txt", "text/plain", "Java".getBytes(StandardCharsets.UTF_8));
        ResumeParseTaskResult failedSource = service.uploadAsync(7L, file, "upload");
        taskExecutor.runNext();
        assertEquals(ResumeParseTaskStatus.FAILED.name(), service.getParseTask(7L, failedSource.taskId()).status());

        ResumeApplicationService retryService = service(
                store,
                ragService,
                mock(CvOptimizationOrchestrator.class),
                (userId, filename, text) -> CvBO.builder().name("retry-cv").summary(text).build(),
                new ResumeRenderService(),
                parseTaskStore,
                taskExecutor
        );
        ResumeParseTaskResult retried = retryService.retryParseTask(7L, failedSource.taskId());

        assertEquals(ResumeParseTaskStatus.PROCESSING.name(), retried.status());
        assertEquals(failedSource.taskId(), retried.retryOfTaskId());
        assertEquals(1, taskExecutor.tasks.size());
    }

    @Test
    void recoverStaleParseTasksRequeuesOriginalTaskIdAndCompletesFromSnapshot() {
        ResumeStore store = mock(ResumeStore.class);
        ResumeRagService ragService = mock(ResumeRagService.class);
        ManualTaskExecutor taskExecutor = new ManualTaskExecutor();
        InMemoryResumeParseTaskStore parseTaskStore = new InMemoryResumeParseTaskStore();
        Instant staleTime = Instant.now().minusSeconds(7200);
        ResumeParseTaskRecord stale = parseTask("stale-task", ResumeParseTaskStatus.ANALYZING, staleTime, "Java Redis".getBytes(StandardCharsets.UTF_8));
        parseTaskStore.save(stale);
        ResumeApplicationService service = service(
                store,
                ragService,
                mock(CvOptimizationOrchestrator.class),
                (userId, filename, text) -> CvBO.builder().name("recovered-cv").summary(text).build(),
                new ResumeRenderService(),
                parseTaskStore,
                taskExecutor
        );
        when(store.save(any())).thenAnswer(invocation -> ((CvBO) invocation.getArgument(0)).toBuilder().id(41L).userId(7L).build());
        when(store.findByIdAndUserId(41L, 7L)).thenReturn(Optional.of(CvBO.builder().id(41L).userId(7L).name("recovered-cv").summary("Java Redis").build()));
        when(ragService.storeCvBO(any())).thenReturn(List.of());

        int recovered = service.recoverStaleParseTasks(Duration.ofMinutes(30), 10);

        assertEquals(1, recovered);
        assertEquals(1, taskExecutor.tasks.size());
        assertEquals(ResumeParseTaskStatus.ANALYZING.name(), service.getParseTask(7L, "stale-task").status());
        taskExecutor.runNext();
        ResumeParseTaskResult completed = service.getParseTask(7L, "stale-task");
        assertEquals(ResumeParseTaskStatus.COMPLETED.name(), completed.status());
        assertEquals(41L, completed.resumeId());
    }

    @Test
    void recoverStaleParseTasksFailsClosedWhenPayloadIsMissing() {
        ManualTaskExecutor taskExecutor = new ManualTaskExecutor();
        InMemoryResumeParseTaskStore parseTaskStore = new InMemoryResumeParseTaskStore();
        ResumeParseTaskRecord staleWithoutPayload = parseTask(
                "missing-payload",
                ResumeParseTaskStatus.PROCESSING,
                Instant.now().minusSeconds(7200),
                new byte[0]
        );
        parseTaskStore.save(staleWithoutPayload);
        ResumeApplicationService service = service(
                mock(ResumeStore.class),
                mock(ResumeRagService.class),
                mock(CvOptimizationOrchestrator.class),
                (userId, filename, text) -> CvBO.builder().name("unused").summary(text).build(),
                new ResumeRenderService(),
                parseTaskStore,
                taskExecutor
        );

        int recovered = service.recoverStaleParseTasks(Duration.ofMinutes(30), 10);

        assertEquals(1, recovered);
        assertEquals(0, taskExecutor.tasks.size());
        ResumeParseTaskResult failed = service.getParseTask(7L, "missing-payload");
        assertEquals(ResumeParseTaskStatus.FAILED.name(), failed.status());
        org.junit.jupiter.api.Assertions.assertTrue(failed.errorMessage().contains("missing recoverable file payload"));
    }

    @Test
    void recoverStaleSavingTaskCompletesExistingResumeWithoutSavingDuplicate() {
        ResumeStore store = mock(ResumeStore.class);
        ResumeRagService ragService = mock(ResumeRagService.class);
        ManualTaskExecutor taskExecutor = new ManualTaskExecutor();
        InMemoryResumeParseTaskStore parseTaskStore = new InMemoryResumeParseTaskStore();
        ResumeParseTaskRecord staleSaving = parseTask(
                "saving-task",
                ResumeParseTaskStatus.SAVING,
                Instant.now().minusSeconds(7200),
                new byte[0],
                51L
        );
        parseTaskStore.save(staleSaving);
        CvBO existing = CvBO.builder().id(51L).userId(7L).name("existing-cv").summary("Java Redis").build();
        when(store.findByIdAndUserId(51L, 7L)).thenReturn(Optional.of(existing));
        when(ragService.storeCvBO(existing)).thenReturn(List.of());
        ResumeApplicationService service = service(
                store,
                ragService,
                mock(CvOptimizationOrchestrator.class),
                (userId, filename, text) -> CvBO.builder().name("duplicate").summary(text).build(),
                new ResumeRenderService(),
                parseTaskStore,
                taskExecutor
        );

        int recovered = service.recoverStaleParseTasks(Duration.ofMinutes(30), 10);
        taskExecutor.runNext();

        assertEquals(1, recovered);
        ResumeParseTaskResult completed = service.getParseTask(7L, "saving-task");
        assertEquals(ResumeParseTaskStatus.COMPLETED.name(), completed.status());
        assertEquals(51L, completed.resumeId());
        verify(store, never()).save(any());
        verify(ragService).storeCvBO(existing);
    }

    private ResumeApplicationService service(ResumeStore store, ResumeRagService ragService, CvOptimizationOrchestrator orchestrator) {
        return service(store, ragService, orchestrator, (userId, filename, text) -> null);
    }

    private ResumeApplicationService service(
            ResumeStore store,
            ResumeRagService ragService,
            CvOptimizationOrchestrator orchestrator,
            ResumeRenderService renderService) {
        return service(store, ragService, orchestrator, (userId, filename, text) -> null, renderService);
    }

    private ResumeApplicationService service(
            ResumeStore store,
            ResumeRagService ragService,
            CvOptimizationOrchestrator orchestrator,
            ResumeStructuringService structuringService) {
        return service(store, ragService, orchestrator, structuringService, new ResumeRenderService());
    }

    private ResumeApplicationService service(
            ResumeStore store,
            ResumeRagService ragService,
            CvOptimizationOrchestrator orchestrator,
            ResumeStructuringService structuringService,
            ResumeRenderService renderService) {
        return service(
                store,
                ragService,
                orchestrator,
                structuringService,
                renderService,
                new InMemoryResumeParseTaskStore(),
                Runnable::run
        );
    }

    private ResumeApplicationService service(
            ResumeStore store,
            ResumeRagService ragService,
            CvOptimizationOrchestrator orchestrator,
            ResumeStructuringService structuringService,
            ResumeRenderService renderService,
            ResumeParseTaskStore parseTaskStore,
            TaskExecutor taskExecutor) {
        return service(store, ragService, orchestrator, structuringService, renderService, parseTaskStore, taskExecutor, ResumeObjectStorage.disabled());
    }

    private ResumeApplicationService service(
            ResumeStore store,
            ResumeRagService ragService,
            CvOptimizationOrchestrator orchestrator,
            ResumeStructuringService structuringService,
            ResumeRenderService renderService,
            ResumeParseTaskStore parseTaskStore,
            TaskExecutor taskExecutor,
            ResumeObjectStorage objectStorage) {
        return new ResumeApplicationService(
                store,
                mock(JobMatchTaskStore.class),
                parseTaskStore,
                ragService,
                orchestrator,
                mock(InterviewPlanningService.class),
                mock(CareerInterviewExecutionBridge.class),
                new HybridCompactingChatMemory(request -> null, new InterviewRuleBasedScorer(), new DecisionIndex()),
                structuringService,
                new ResumePdfTextExtractor(),
                renderService,
                taskExecutor,
                emptyProvider(),
                objectStorage
        );
    }

    private static class ManualTaskExecutor implements TaskExecutor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        void runNext() {
            tasks.remove(0).run();
        }
    }

    private ResumeParseTaskRecord parseTask(String taskId, ResumeParseTaskStatus status, Instant updateTime, byte[] snapshot) {
        return parseTask(taskId, status, updateTime, snapshot, null);
    }

    private ResumeParseTaskRecord parseTask(String taskId, ResumeParseTaskStatus status, Instant updateTime, byte[] snapshot, Long resumeId) {
        return new ResumeParseTaskRecord(
                taskId,
                7L,
                status,
                resumeId,
                "resume.txt",
                snapshot == null ? 0L : (long) snapshot.length,
                "text/plain",
                "upload",
                snapshot == null || snapshot.length == 0 ? "local-snapshot" : "local-snapshot",
                taskId,
                null,
                snapshot,
                null,
                null,
                updateTime.minusSeconds(60),
                status.terminal() ? updateTime : null,
                updateTime.minusSeconds(120),
                updateTime
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
