package com.hewei.hzyjy.xunzhi.career.raglab.application;

import com.alibaba.fastjson2.JSON;
import com.hewei.hzyjy.xunzhi.career.harness.application.AgentRunCoordinator;
import com.hewei.hzyjy.xunzhi.career.harness.model.AgentRunStartCommand;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagExperimentDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagExperimentDatasetItemDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagExperimentJudgementMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagExperimentMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagExperimentResultMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagResumeTagMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.model.RagExperimentMetrics;
import com.hewei.hzyjy.xunzhi.career.raglab.model.RagExperimentRuntimeOptions;
import com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagMatch;
import com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class RagExperimentHarnessIntegrationTest {

    @Test
    void recordsRagExperimentStagesWithoutPersistingJobDescriptionInHarnessRun() {
        RagExperimentDatasetService datasetService = mock(RagExperimentDatasetService.class);
        RagExperimentMetricCalculator metricCalculator = mock(RagExperimentMetricCalculator.class);
        RagExperimentEvidenceMetricCalculator evidenceMetricCalculator = mock(RagExperimentEvidenceMetricCalculator.class);
        ResumeRagService resumeRagService = mock(ResumeRagService.class);
        RagExperimentMapper experimentMapper = mock(RagExperimentMapper.class);
        RagExperimentJudgementMapper judgementMapper = mock(RagExperimentJudgementMapper.class);
        RagExperimentResultMapper resultMapper = mock(RagExperimentResultMapper.class);
        RagResumeTagMapper tagMapper = mock(RagResumeTagMapper.class);
        AgentRunCoordinator coordinator = mock(AgentRunCoordinator.class);

        RagExperimentDO experiment = new RagExperimentDO();
        experiment.setId(88L);
        experiment.setOwnerUserId(7L);
        experiment.setDatasetId(11L);
        experiment.setTopK(3);
        experiment.setJobDescription("Java 后端岗位，含有不应写入 Harness 的 JD 原文");
        experiment.setConfigFingerprint("experiment-config-88");
        experiment.setRuntimeConfigJson(JSON.toJSONString(new RagExperimentRuntimeOptions(
                true, false, false, true, true, true, true, false, 3, Map.of())));
        when(experimentMapper.selectById(88L)).thenReturn(experiment);
        RagExperimentDatasetItemDO item = new RagExperimentDatasetItemDO();
        item.setResumeId(59L);
        when(datasetService.listItems(11L)).thenReturn(List.of(item));
        when(resumeRagService.retrieveExperimentMatches(any(), anyLong(), any(), any(), any()))
                .thenReturn(List.of(new ResumeRagMatch("59", 0.91D, List.of("skills: Java"))));
        when(resumeRagService.consumeExperimentStageTrace("rag-exp-88"))
                .thenReturn(Map.of("fallbackStageCount", 0));
        when(metricCalculator.calculate(any(), any(), anyInt()))
                .thenReturn(new RagExperimentMetrics(1D, 1D, 1D, 1D, 1D, 1, 1, 1));
        when(coordinator.start(any())).thenReturn("agent-run-88");

        RagExperimentService service = new RagExperimentService(
                datasetService, metricCalculator, evidenceMetricCalculator, resumeRagService, experimentMapper,
                judgementMapper, resultMapper, tagMapper, Runnable::run);
        ReflectionTestUtils.setField(service, "agentRunCoordinator", coordinator);

        service.run(88L);

        ArgumentCaptor<AgentRunStartCommand> commandCaptor = ArgumentCaptor.forClass(AgentRunStartCommand.class);
        verify(coordinator).start(commandCaptor.capture());
        assertEquals("RAG_EXPERIMENT", commandCaptor.getValue().sceneCode());
        assertEquals("88", commandCaptor.getValue().businessId());
        assertEquals("experiment-config-88", commandCaptor.getValue().configFingerprint());
        org.junit.jupiter.api.Assertions.assertFalse(commandCaptor.getValue().inputSummary().contains("Java 后端岗位"));
        verify(coordinator, atLeastOnce()).stage(eq("agent-run-88"), eq("JD_SAFETY_CHECK"), any(), any());
        verify(coordinator, atLeastOnce()).stage(eq("agent-run-88"), eq("RAG_RETRIEVE"), any(), any());
        verify(coordinator, atLeastOnce()).stage(eq("agent-run-88"), eq("METRIC_CALCULATE"), any(), any());
        verify(coordinator).succeed(eq("agent-run-88"), any(), any());
    }
}
