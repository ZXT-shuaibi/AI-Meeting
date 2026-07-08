package com.hewei.hzyjy.xunzhi.career.api;

import com.hewei.hzyjy.xunzhi.career.agent.cv.CvOptimizationResult;
import com.hewei.hzyjy.xunzhi.career.api.io.ResumeOptimizeReqDTO;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeApplicationService;
import com.hewei.hzyjy.xunzhi.common.convention.context.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
