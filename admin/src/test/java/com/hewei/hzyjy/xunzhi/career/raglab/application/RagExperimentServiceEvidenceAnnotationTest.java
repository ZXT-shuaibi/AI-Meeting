package com.hewei.hzyjy.xunzhi.career.raglab.application;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.UpdateRagEvidenceAnnotationsReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagExperimentDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagExperimentResultDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagExperimentJudgementMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagExperimentMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagExperimentResultMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagResumeTagMapper;
import com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagService;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RagExperimentServiceEvidenceAnnotationTest {

    @Test
    void annotationUpdatesEvidenceSnapshotAndChunkAccuracyTogether() {
        RagExperimentMapper experimentMapper = mock(RagExperimentMapper.class);
        RagExperimentResultMapper resultMapper = mock(RagExperimentResultMapper.class);
        RagExperimentDO experiment = new RagExperimentDO();
        experiment.setId(1L);
        experiment.setOwnerUserId(9L);
        experiment.setMetricSnapshotJson("{\"recallAtK\":0.5,\"chunkHitAccuracy\":null}");
        RagExperimentResultDO result = new RagExperimentResultDO();
        result.setExperimentId(1L);
        result.setResumeId(7L);
        result.setMatchedChunksJson("[\"命中了 Java 项目经验\",\"无关教育信息\"]");
        when(experimentMapper.selectById(1L)).thenReturn(experiment);
        when(resultMapper.selectOne(any(Wrapper.class))).thenReturn(result);
        when(resultMapper.selectList(any(Wrapper.class))).thenReturn(List.of(result));

        RagExperimentService service = new RagExperimentService(
                mock(RagExperimentDatasetService.class),
                mock(RagExperimentMetricCalculator.class),
                new RagExperimentEvidenceMetricCalculator(),
                mock(ResumeRagService.class),
                experimentMapper,
                mock(RagExperimentJudgementMapper.class),
                resultMapper,
                mock(RagResumeTagMapper.class),
                mock(TaskExecutor.class));
        UpdateRagEvidenceAnnotationsReqDTO.Item item = new UpdateRagEvidenceAnnotationsReqDTO.Item();
        item.setResumeId(7L);
        item.setEvidenceIndex(0);
        item.setHit(true);

        service.updateEvidenceAnnotations(1L, 9L, List.of(item));

        JSONArray evidence = JSON.parseArray(result.getMatchedChunksJson());
        JSONObject metrics = JSON.parseObject(experiment.getMetricSnapshotJson());
        assertEquals(true, evidence.getJSONObject(0).get("hit"));
        assertEquals("命中了 Java 项目经验", evidence.getJSONObject(0).get("text"));
        assertEquals(1.0D, ((Number) metrics.get("chunkHitAccuracy")).doubleValue());
        assertEquals("已标注 1 个证据片段", metrics.get("chunkHitAccuracyStatus"));
        verify(resultMapper).updateById(result);
        verify(experimentMapper).updateById(experiment);
    }
}
