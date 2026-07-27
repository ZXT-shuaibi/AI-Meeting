package com.hewei.hzyjy.xunzhi.career.raglab.application;

import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagResumeAutoTagTaskDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagResumeAutoTagTaskMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.model.RagResumeAutoTagTaskResult;
import com.hewei.hzyjy.xunzhi.career.raglab.model.RagResumeAutoTagTaskStatus;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RagResumeAutoTagTaskServiceTest {

    @Test
    void reusesTheExistingProcessingTaskInsteadOfCallingTheModelTwice() {
        RagResumeAutoTagTaskMapper taskMapper = mock(RagResumeAutoTagTaskMapper.class);
        RagResumeAutoTagService autoTagService = mock(RagResumeAutoTagService.class);
        RagResumeAutoTagTaskDO running = new RagResumeAutoTagTaskDO();
        running.setTaskId("tag-task-running");
        running.setStatus(RagResumeAutoTagTaskStatus.PROCESSING.name());
        when(taskMapper.selectOne(any())).thenReturn(running);

        RagResumeAutoTagTaskService service = new RagResumeAutoTagTaskService(
                autoTagService, taskMapper, runnable -> fail("不应重复提交模型任务"));

        RagResumeAutoTagTaskResult result = service.submit(9L, 8L, CvBO.builder().id(8L).userId(9L).build());

        assertEquals("tag-task-running", result.taskId());
        assertEquals(RagResumeAutoTagTaskStatus.PROCESSING, result.status());
        assertTrue(result.reused());
        verifyNoInteractions(autoTagService);
        verify(taskMapper, never()).insert(any(RagResumeAutoTagTaskDO.class));
    }

    @Test
    void completesTheBackgroundTaskAndRecordsTheGeneratedTagCount() {
        RagResumeAutoTagTaskMapper taskMapper = mock(RagResumeAutoTagTaskMapper.class);
        RagResumeAutoTagService autoTagService = mock(RagResumeAutoTagService.class);
        AtomicReference<Runnable> pendingJob = new AtomicReference<>();
        TaskExecutor executor = pendingJob::set;
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(autoTagService.evaluateOnDemand(any())).thenReturn(List.of(mock(com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagResumeTagDO.class)));

        RagResumeAutoTagTaskService service = new RagResumeAutoTagTaskService(autoTagService, taskMapper, executor);
        RagResumeAutoTagTaskResult accepted = service.submit(9L, 8L, CvBO.builder().id(8L).userId(9L).build());

        assertEquals(RagResumeAutoTagTaskStatus.PROCESSING, accepted.status());
        assertFalse(accepted.reused());
        assertTrue(pendingJob.get() != null);

        pendingJob.get().run();

        verify(taskMapper).updateById(argThat((RagResumeAutoTagTaskDO task) -> RagResumeAutoTagTaskStatus.COMPLETED.name().equals(task.getStatus())
                && Integer.valueOf(1).equals(task.getTagCount()) && task.getActiveKey() == null));
    }
}
