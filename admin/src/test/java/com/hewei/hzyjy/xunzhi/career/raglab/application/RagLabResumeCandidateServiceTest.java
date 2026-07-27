package com.hewei.hzyjy.xunzhi.career.raglab.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeObjectStorage;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeParseTaskDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeParseTaskMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RagLabResumeCandidateServiceTest {

    @Test
    void keepsOnlyTheNewestCandidateWhenCompletedPdfBytesHaveSameMd5() {
        CareerResumeParseTaskMapper taskMapper = mock(CareerResumeParseTaskMapper.class);
        CareerResumeDO newest = resume(21L, 7L, "newest.pdf");
        CareerResumeDO duplicate = resume(20L, 7L, "duplicate.pdf");
        when(taskMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(task(21L, 7L, "same-pdf"), task(20L, 7L, "same-pdf"));
        RagLabResumeCandidateService service = new RagLabResumeCandidateService(
                taskMapper, ResumeObjectStorage.disabled());

        List<CareerResumeDO> result = service.deduplicateCompletedPdfCandidates(List.of(newest, duplicate));

        assertEquals(List.of(newest), result);
    }

    private CareerResumeDO resume(Long id, Long userId, String name) {
        CareerResumeDO resume = new CareerResumeDO();
        resume.setId(id);
        resume.setUserId(userId);
        resume.setName(name);
        return resume;
    }

    private CareerResumeParseTaskDO task(Long resumeId, Long userId, String content) {
        CareerResumeParseTaskDO task = new CareerResumeParseTaskDO();
        task.setResumeId(resumeId);
        task.setUserId(userId);
        task.setStatus("COMPLETED");
        task.setFileSnapshot(content.getBytes(StandardCharsets.UTF_8));
        return task;
    }
}
