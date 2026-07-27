package com.hewei.hzyjy.xunzhi.career.raglab.api;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hewei.hzyjy.xunzhi.career.raglab.application.RagExperimentDatasetService;
import com.hewei.hzyjy.xunzhi.career.raglab.application.RagExperimentService;
import com.hewei.hzyjy.xunzhi.career.raglab.application.RagLabAccessService;
import com.hewei.hzyjy.xunzhi.career.raglab.application.RagLabResumeCandidateService;
import com.hewei.hzyjy.xunzhi.career.raglab.application.RagResumeAutoTagService;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagResumeTagDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagExperimentDatasetMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagResumeTagMapper;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeObjectStorage;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeParseTaskDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeParseTaskMapper;
import com.hewei.hzyjy.xunzhi.career.resume.render.ResumeRenderArtifact;
import com.hewei.hzyjy.xunzhi.career.resume.render.ResumeRenderService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RagLabControllerSourcePdfTest {

    @Test
    void rendersStructuredResumeWhenHistoricalSourcePdfIsUnavailable() {
        CareerResumeDO resume = new CareerResumeDO();
        resume.setId(7L);
        resume.setUserId(9L);
        resume.setName("历史简历.pdf");
        resume.setCvJson("{\"id\":7,\"userId\":9,\"name\":\"历史简历\"}");
        CareerResumeParseTaskDO task = new CareerResumeParseTaskDO();
        task.setUserId(9L);
        task.setResumeId(7L);
        task.setStatus("COMPLETED");
        task.setOriginalFilename("历史简历.pdf");
        task.setContentType("application/pdf");
        task.setFileSnapshot(new byte[0]);
        CareerResumeMapper resumeMapper = mock(CareerResumeMapper.class);
        CareerResumeParseTaskMapper taskMapper = mock(CareerResumeParseTaskMapper.class);
        ResumeObjectStorage storage = mock(ResumeObjectStorage.class);
        ResumeRenderService renderService = mock(ResumeRenderService.class);
        when(resumeMapper.selectById(7L)).thenReturn(resume);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(storage.enabled()).thenReturn(false);
        when(renderService.renderPdf(any())).thenReturn(new ResumeRenderArtifact(
                "pdf", "历史简历.pdf", "application/pdf", "", new byte[]{37, 80, 68, 70}));

        RagLabController controller = new RagLabController(
                mock(RagLabAccessService.class),
                mock(RagLabResumeCandidateService.class),
                mock(RagExperimentDatasetService.class),
                mock(RagExperimentDatasetMapper.class),
                mock(RagResumeTagMapper.class),
                mock(RagExperimentService.class),
                resumeMapper,
                taskMapper,
                storage,
                renderService,
                mock(RagResumeAutoTagService.class));

        ResponseEntity<byte[]> response = controller.sourcePdf(7L, 9L, "tester");

        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertEquals("rendered-fallback", response.getHeaders().getFirst("X-Rag-Lab-Pdf-Source"));
        assertArrayEquals(new byte[]{37, 80, 68, 70}, response.getBody());
        verify(renderService).renderPdf(any());
    }
}
