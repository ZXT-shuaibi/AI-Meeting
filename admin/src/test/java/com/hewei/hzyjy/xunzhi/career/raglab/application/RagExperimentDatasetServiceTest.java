package com.hewei.hzyjy.xunzhi.career.raglab.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagExperimentDatasetDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagExperimentDatasetItemMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagExperimentDatasetMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagResumeTagMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeParseTaskDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeParseTaskMapper;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RagExperimentDatasetServiceTest {

    @Test
    void administratorCreatesDatasetInSelectedResumeOwnersSpace() {
        CareerResumeMapper resumeMapper = mock(CareerResumeMapper.class);
        CareerResumeParseTaskMapper parseTaskMapper = mock(CareerResumeParseTaskMapper.class);
        RagExperimentDatasetMapper datasetMapper = mock(RagExperimentDatasetMapper.class);
        RagExperimentDatasetService service = new RagExperimentDatasetService(
                datasetMapper,
                mock(RagExperimentDatasetItemMapper.class),
                mock(RagResumeTagMapper.class),
                resumeMapper,
                parseTaskMapper);
        CareerResumeDO resume = new CareerResumeDO();
        resume.setId(42L);
        resume.setUserId(200L);
        when(resumeMapper.selectById(42L)).thenReturn(resume);
        when(parseTaskMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);

        service.create(100L, true, "候选集", null, List.of(42L));

        ArgumentCaptor<RagExperimentDatasetDO> captor = ArgumentCaptor.forClass(RagExperimentDatasetDO.class);
        verify(datasetMapper).insert(captor.capture());
        assertEquals(200L, captor.getValue().getOwnerUserId());
    }

    @Test
    void administratorCannotMixResumesFromDifferentOwnersInOneDataset() {
        CareerResumeMapper resumeMapper = mock(CareerResumeMapper.class);
        CareerResumeParseTaskMapper parseTaskMapper = mock(CareerResumeParseTaskMapper.class);
        RagExperimentDatasetService service = new RagExperimentDatasetService(
                mock(RagExperimentDatasetMapper.class),
                mock(RagExperimentDatasetItemMapper.class),
                mock(RagResumeTagMapper.class),
                resumeMapper,
                parseTaskMapper);
        CareerResumeDO first = new CareerResumeDO();
        first.setId(42L);
        first.setUserId(200L);
        CareerResumeDO second = new CareerResumeDO();
        second.setId(43L);
        second.setUserId(201L);
        when(resumeMapper.selectById(42L)).thenReturn(first);
        when(resumeMapper.selectById(43L)).thenReturn(second);

        ClientException error = assertThrows(ClientException.class,
                () -> service.create(100L, true, "候选集", null, List.of(42L, 43L)));

        assertEquals("同一个 RAG 测试集只能选择同一用户的简历", error.getMessage());
        verifyNoInteractions(parseTaskMapper);
    }

    @Test
    void updatesDatasetNameAndDescriptionWithoutChangingItsOwnerOrItems() {
        RagExperimentDatasetMapper datasetMapper = mock(RagExperimentDatasetMapper.class);
        RagExperimentDatasetService service = new RagExperimentDatasetService(
                datasetMapper,
                mock(RagExperimentDatasetItemMapper.class),
                mock(RagResumeTagMapper.class),
                mock(CareerResumeMapper.class),
                mock(CareerResumeParseTaskMapper.class));
        RagExperimentDatasetDO dataset = new RagExperimentDatasetDO();
        dataset.setId(7L);
        dataset.setOwnerUserId(200L);
        dataset.setName("旧名称");
        when(datasetMapper.selectById(7L)).thenReturn(dataset);

        service.updateMetadata(7L, "  新名称  ", "新的说明");

        assertEquals("新名称", dataset.getName());
        assertEquals("新的说明", dataset.getDescription());
        assertEquals(200L, dataset.getOwnerUserId());
        verify(datasetMapper).updateById(dataset);
    }
}
