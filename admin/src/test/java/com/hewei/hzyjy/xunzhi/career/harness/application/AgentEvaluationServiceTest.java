package com.hewei.hzyjy.xunzhi.career.harness.application;

import com.alibaba.fastjson2.JSON;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentEvaluationBaselineDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentEvaluationBaselineMapper;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentRunMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagExperimentDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagExperimentMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentEvaluationServiceTest {

    @Test
    void freezesCompletedRagExperimentAsBaselineAndComputesMetricDeltas() {
        RagExperimentMapper experimentMapper = mock(RagExperimentMapper.class);
        AgentEvaluationBaselineMapper baselineMapper = mock(AgentEvaluationBaselineMapper.class);
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        RagExperimentDO baselineExperiment = experiment(10L, 7L, "COMPLETED", 0.70D, 0.60D, 1_200L);
        RagExperimentDO currentExperiment = experiment(11L, 7L, "COMPLETED", 0.80D, 0.75D, 1_000L);
        when(experimentMapper.selectById(10L)).thenReturn(baselineExperiment);
        when(experimentMapper.selectById(11L)).thenReturn(currentExperiment);
        doAnswer(invocation -> { ((AgentEvaluationBaselineDO) invocation.getArgument(0)).setId(99L); return 1; })
                .when(baselineMapper).insert(any(AgentEvaluationBaselineDO.class));
        AgentEvaluationService service = new AgentEvaluationService(experimentMapper, baselineMapper, runMapper);

        AgentEvaluationBaselineDO baseline = service.createRagBaseline("默认检索基线", 10L);
        when(baselineMapper.selectById(99L)).thenReturn(baseline);
        Map<String, Object> comparison = service.compareRagExperiment(99L, 11L);

        assertEquals("RAG_EXPERIMENT", baseline.getSceneCode());
        assertEquals(10L, baseline.getSourceExperimentId());
        @SuppressWarnings("unchecked") Map<String, Double> deltas = (Map<String, Double>) comparison.get("metricDeltas");
        assertEquals(0.10D, deltas.get("recallAtK"), 0.0001D);
        assertEquals(-200D, deltas.get("totalDurationMs"), 0.0001D);
    }

    @Test
    void aggregatesAgentRunQualityByScene() {
        RagExperimentMapper experimentMapper = mock(RagExperimentMapper.class);
        AgentEvaluationBaselineMapper baselineMapper = mock(AgentEvaluationBaselineMapper.class);
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        when(runMapper.selectList(any())).thenReturn(List.of(
                run("RESUME_TAILOR", "SUCCEEDED", true, 100L),
                run("RESUME_TAILOR", "FAILED", true, 300L),
                run("INTERVIEW_REPORT", "SUCCEEDED", false, 50L)
        ));
        AgentEvaluationService service = new AgentEvaluationService(experimentMapper, baselineMapper, runMapper);

        List<Map<String, Object>> quality = service.sceneQuality();

        assertEquals(2, quality.size());
        Map<String, Object> resumeQuality = quality.stream().filter(row -> "RESUME_TAILOR".equals(row.get("sceneCode"))).findFirst().orElseThrow();
        assertEquals(50D, resumeQuality.get("successRate"));
        assertEquals(100D, resumeQuality.get("ragUsageRate"));
    }

    @Test
    void rejectsComparisonWhenExperimentsUseDifferentDataset() {
        RagExperimentMapper experimentMapper = mock(RagExperimentMapper.class);
        AgentEvaluationBaselineMapper baselineMapper = mock(AgentEvaluationBaselineMapper.class);
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        RagExperimentDO source = experiment(10L, 7L, "COMPLETED", 0.70D, 0.60D, 1_200L);
        RagExperimentDO differentDataset = experiment(11L, 7L, "COMPLETED", 0.80D, 0.75D, 1_000L);
        source.setDatasetId(100L);
        differentDataset.setDatasetId(101L);
        source.setJobDescription("Java 后端岗位");
        differentDataset.setJobDescription("Java 后端岗位");
        source.setJudgementSnapshotJson("[{\"tag\":\"后端\",\"score\":3}]");
        differentDataset.setJudgementSnapshotJson(source.getJudgementSnapshotJson());
        when(experimentMapper.selectById(10L)).thenReturn(source);
        when(experimentMapper.selectById(11L)).thenReturn(differentDataset);
        doAnswer(invocation -> { ((AgentEvaluationBaselineDO) invocation.getArgument(0)).setId(99L); return 1; })
                .when(baselineMapper).insert(any(AgentEvaluationBaselineDO.class));
        AgentEvaluationService service = new AgentEvaluationService(experimentMapper, baselineMapper, runMapper);

        AgentEvaluationBaselineDO baseline = service.createRagBaseline("后端基线", 10L);
        when(baselineMapper.selectById(99L)).thenReturn(baseline);

        assertThrows(com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException.class,
                () -> service.compareRagExperiment(99L, 11L),
                "不同测试集的指标不能作为算法效果差异进行比较");
    }

    private RagExperimentDO experiment(Long id, Long owner, String status, double recall, double ndcg, long durationMs) {
        RagExperimentDO item = new RagExperimentDO();
        item.setId(id); item.setOwnerUserId(owner); item.setStatus(status);
        item.setDatasetId(100L); item.setTopK(3); item.setJobDescription("Java 后端岗位");
        item.setJudgementSnapshotJson("[{\"tag\":\"后端\",\"score\":3}]");
        item.setName("实验-" + id); item.setRuntimeConfigJson("{\"rerankEnabled\":true}");
        item.setConfigFingerprint("cfg-" + id);
        item.setMetricSnapshotJson(JSON.toJSONString(Map.of("recallAtK", recall, "ndcgAtK", ndcg, "totalDurationMs", durationMs, "precisionAtK", recall, "mrr", recall, "strongRecallAtK", recall, "fallbackStageCount", 0)));
        return item;
    }
    private AgentRunDO run(String scene, String status, boolean rag, long duration) { AgentRunDO run = new AgentRunDO(); run.setSceneCode(scene); run.setStatus(status); run.setRagEnabled(rag); run.setDurationMs(duration); return run; }
}
