package com.hewei.hzyjy.xunzhi.career.api;

import com.hewei.hzyjy.xunzhi.career.agent.cv.CvOptimizationResult;
import com.hewei.hzyjy.xunzhi.career.api.io.ResumeOptimizeReqDTO;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeApplicationService;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeParseTaskResult;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeParseTaskStatus;
import com.hewei.hzyjy.xunzhi.career.resume.render.ResumeRenderArtifact;
import com.hewei.hzyjy.xunzhi.common.convention.context.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResumeCareerControllerTest {

    @Test
    void optimizeStreamReturnsEmitterBeforeOptimizationTaskRuns() throws Exception {
        ResumeApplicationService service = mock(ResumeApplicationService.class);
        ManualTaskExecutor taskExecutor = new ManualTaskExecutor();
        ResumeCareerController controller = new ResumeCareerController(service, taskExecutor);
        ResumeOptimizeReqDTO request = new ResumeOptimizeReqDTO();
        request.setJobDescription("Java backend JD");
        UserContext user = new UserContext(7L, "candidate");
        when(service.optimize(7L, 11L, "Java backend JD")).thenReturn(CvOptimizationResult.builder()
                .iterations(1)
                .scoreGatePassed(true)
                .reviewHistory(List.of())
                .build());

        SseEmitter emitter = controller.optimizeStream(11L, request, user);

        assertNotNull(emitter);
        verifyNoInteractions(service);
        assertEquals(1, taskExecutor.tasks.size());

        taskExecutor.runNext();

        verify(service).optimize(7L, 11L, "Java backend JD");
    }

    @Test
    void renderResumeReturnsDownloadResponseForRequestedFormat() {
        ResumeApplicationService service = mock(ResumeApplicationService.class);
        ResumeCareerController controller = new ResumeCareerController(service, Runnable::run);
        UserContext user = new UserContext(7L, "candidate");
        when(service.renderResume(7L, 11L, "markdown")).thenReturn(new ResumeRenderArtifact(
                "markdown",
                "candidate.md",
                "text/markdown;charset=UTF-8",
                "# candidate",
                "# candidate".getBytes(StandardCharsets.UTF_8)
        ));

        ResponseEntity<byte[]> response = controller.renderResume(11L, "markdown", user);

        assertEquals(MediaType.parseMediaType("text/markdown;charset=UTF-8"), response.getHeaders().getContentType());
        assertEquals("# candidate", new String(response.getBody(), StandardCharsets.UTF_8));
        org.junit.jupiter.api.Assertions.assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("candidate.md"));
        verify(service).renderResume(7L, 11L, "markdown");
    }

    @Test
    void uploadAsyncReturnsParseTaskAndDelegatesCurrentUser() {
        ResumeApplicationService service = mock(ResumeApplicationService.class);
        ResumeCareerController controller = new ResumeCareerController(service, Runnable::run);
        UserContext user = new UserContext(7L, "candidate");
        MockMultipartFile file = new MockMultipartFile("resume", "resume.txt", "text/plain", "Java".getBytes(StandardCharsets.UTF_8));
        ResumeParseTaskResult task = parseTask("task-1", ResumeParseTaskStatus.PROCESSING);
        when(service.uploadAsync(7L, file, "upload")).thenReturn(task);

        assertEquals(task, controller.uploadAsync(file, "upload", user).getData());

        verify(service).uploadAsync(7L, file, "upload");
    }

    @Test
    void parseTaskStatusCancelAndRetryUseCurrentUserScope() {
        ResumeApplicationService service = mock(ResumeApplicationService.class);
        ResumeCareerController controller = new ResumeCareerController(service, Runnable::run);
        UserContext user = new UserContext(7L, "candidate");
        when(service.getParseTask(7L, "task-1")).thenReturn(parseTask("task-1", ResumeParseTaskStatus.ANALYZING));
        when(service.cancelParseTask(7L, "task-1")).thenReturn(parseTask("task-1", ResumeParseTaskStatus.CANCELED));
        when(service.retryParseTask(7L, "task-1")).thenReturn(parseTask("task-2", ResumeParseTaskStatus.PROCESSING));

        assertEquals(ResumeParseTaskStatus.ANALYZING.name(), controller.getParseTask("task-1", user).getData().status());
        assertEquals(ResumeParseTaskStatus.CANCELED.name(), controller.cancelParseTask("task-1", user).getData().status());
        assertEquals("task-2", controller.retryParseTask("task-1", user).getData().taskId());

        verify(service).getParseTask(7L, "task-1");
        verify(service).cancelParseTask(7L, "task-1");
        verify(service).retryParseTask(7L, "task-1");
    }

    @Test
    void listParseTasksUsesCurrentUserScopeAndOptionalStatus() {
        ResumeApplicationService service = mock(ResumeApplicationService.class);
        ResumeCareerController controller = new ResumeCareerController(service, Runnable::run);
        UserContext user = new UserContext(7L, "candidate");
        List<ResumeParseTaskResult> tasks = List.of(
                parseTask("task-1", ResumeParseTaskStatus.PROCESSING),
                parseTask("task-2", ResumeParseTaskStatus.COMPLETED)
        );
        when(service.listParseTasks(7L, "PROCESSING")).thenReturn(tasks);

        assertEquals(tasks, controller.listParseTasks("PROCESSING", user).getData());

        verify(service).listParseTasks(7L, "PROCESSING");
    }

    private ResumeParseTaskResult parseTask(String taskId, ResumeParseTaskStatus status) {
        return new ResumeParseTaskResult(taskId, 7L, status.name(), status.message(), 5, 65L, null,
                "resume.txt", 4L, "text/plain", "local-snapshot", taskId, null, null,
                java.time.Instant.now(), null);
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
}
