package com.hewei.hzyjy.xunzhi.career.harness.application;

import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunEventDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentRunEventMapper;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentRunMapper;
import com.hewei.hzyjy.xunzhi.career.harness.model.AgentRunStartCommand;
import com.hewei.hzyjy.xunzhi.career.harness.model.AgentRunStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunCoordinatorTest {

    @Test
    void recordsOrderedStagesAndTerminalStatusForOneBusinessRun() {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentRunEventMapper eventMapper = mock(AgentRunEventMapper.class);
        when(runMapper.insert(any(AgentRunDO.class))).thenReturn(1);
        when(eventMapper.insert(any(AgentRunEventDO.class))).thenReturn(1);

        AgentRunCoordinator coordinator = new AgentRunCoordinator(runMapper, eventMapper);
        String runId = coordinator.start(new AgentRunStartCommand(
                "resume-optimization",
                "RESUME_TAILOR",
                7L,
                "59",
                "resume-optimization:59",
                "trace-59",
                true,
                "JD 摘要哈希=abc",
                "config-hash-1"
        ));

        coordinator.stage(runId, "RAG_RETRIEVE", "检索简历证据", Map.of("resultCount", 3));
        coordinator.succeed(runId, "优化结果已保存", Map.of("iterations", 2));

        ArgumentCaptor<AgentRunDO> runCaptor = ArgumentCaptor.forClass(AgentRunDO.class);
        verify(runMapper).insert(runCaptor.capture());
        assertEquals(AgentRunStatus.RUNNING.name(), runCaptor.getValue().getStatus());
        assertEquals("trace-59", runCaptor.getValue().getTraceId());
        assertNotNull(runId);

        ArgumentCaptor<AgentRunEventDO> eventCaptor = ArgumentCaptor.forClass(AgentRunEventDO.class);
        verify(eventMapper, org.mockito.Mockito.times(3)).insert(eventCaptor.capture());
        assertEquals("RUN_CREATED", eventCaptor.getAllValues().get(0).getEventType());
        assertEquals("RAG_RETRIEVE", eventCaptor.getAllValues().get(1).getEventType());
        assertEquals("RUN_FINISHED", eventCaptor.getAllValues().get(2).getEventType());

        ArgumentCaptor<AgentRunDO> updatedRunCaptor = ArgumentCaptor.forClass(AgentRunDO.class);
        verify(runMapper).update(updatedRunCaptor.capture(), any());
        assertEquals(AgentRunStatus.SUCCEEDED.name(), updatedRunCaptor.getValue().getStatus());
    }
}
