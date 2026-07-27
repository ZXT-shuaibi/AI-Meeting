package com.hewei.hzyjy.xunzhi.career.harness.application;

import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunCheckpointDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentRunCheckpointMapper;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentRunMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentRunCheckpointServiceTest {
 @Test void savesSafeCheckpointAndRecognizesCancellation() {
  AgentRunCheckpointMapper checkpointMapper=mock(AgentRunCheckpointMapper.class); AgentRunMapper runMapper=mock(AgentRunMapper.class);
  when(checkpointMapper.selectList(any())).thenReturn(List.of()); AgentRunDO run=new AgentRunDO(); run.setRunId("run-1"); run.setStatus("RUNNING"); when(runMapper.selectList(any())).thenReturn(List.of(run));
  AgentRunCheckpointService service=new AgentRunCheckpointService(checkpointMapper,runMapper);
  service.checkpoint("run-1","RAG_RETRIEVE",Map.of("resultCount",3,"jd","secret"));
  verify(checkpointMapper).insert(any(AgentRunCheckpointDO.class)); assertFalse(service.isCancelled("run-1"));
  doAnswer(invocation -> { run.setStatus("CANCELLED"); return 1; }).when(runMapper).update(any(AgentRunDO.class),any());
  service.requestCancel("run-1","用户主动取消"); assertTrue(service.isCancelled("run-1"));
 }
}
