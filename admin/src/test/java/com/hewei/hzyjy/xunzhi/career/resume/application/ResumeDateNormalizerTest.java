package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.alibaba.fastjson2.JSON;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResumeDateNormalizerTest {

    @Test
    void normalizesYearMonthToTheFirstDayForLocalDateBinding() {
        String json = "{\"educations\":[{\"startDate\":\"2021-09\",\"endDate\":\"2024-09\"}]}";
        String normalized = ResumeDateNormalizer.normalizeYearMonthDates(json);

        assertEquals(
                "{\"educations\":[{\"startDate\":\"2021-09-01\",\"endDate\":\"2024-09-01\"}]}",
                normalized
        );
        CvBO cv = JSON.parseObject(normalized, CvBO.class);
        assertEquals(LocalDate.of(2024, 9, 1), cv.getEducations().getFirst().getEndDate());
    }
}
