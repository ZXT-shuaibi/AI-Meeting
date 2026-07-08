package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeMapper;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class MySqlResumeStore implements ResumeStore {

    private final ObjectProvider<CareerResumeMapper> mapperProvider;
    private final InMemoryResumeStore fallbackStore;

    @Override
    public CvBO save(CvBO cv) {
        CareerResumeMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return fallbackStore.save(cv);
        }
        try {
            CareerResumeDO row = toRow(cv);
            Date now = new Date();
            row.setUpdateTime(now);
            row.setDelFlag(0);
            if (row.getId() == null) {
                row.setCreateTime(now);
                mapper.insert(row);
            } else {
                CareerResumeDO existing = mapper.selectById(row.getId());
                if (existing == null || Integer.valueOf(1).equals(existing.getDelFlag())) {
                    row.setCreateTime(now);
                    mapper.insert(row);
                } else {
                    mapper.updateById(row);
                }
            }
            CvBO saved = cv.toBuilder().id(row.getId()).build();
            fallbackStore.save(saved);
            return saved;
        } catch (Exception ex) {
            log.warn("MySQL resume store failed, using in-memory fallback. resumeId={}", cv == null ? null : cv.getId(), ex);
            return fallbackStore.save(cv);
        }
    }

    @Override
    public Optional<CvBO> findById(Long resumeId) {
        CareerResumeMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                CareerResumeDO row = mapper.selectOne(Wrappers.<CareerResumeDO>lambdaQuery()
                        .eq(CareerResumeDO::getId, resumeId)
                        .eq(CareerResumeDO::getDelFlag, 0)
                        .last("limit 1"));
                if (row != null) {
                    CvBO cv = fromRow(row);
                    fallbackStore.save(cv);
                    return Optional.of(cv);
                }
            } catch (Exception ex) {
                log.warn("MySQL resume lookup failed, using in-memory fallback. resumeId={}", resumeId, ex);
            }
        }
        return fallbackStore.findById(resumeId);
    }

    private CareerResumeDO toRow(CvBO cv) {
        CareerResumeDO row = new CareerResumeDO();
        row.setId(cv.getId());
        row.setUserId(cv.getUserId());
        row.setCvType(defaultString(cv.getCvType(), "upload"));
        row.setName(cv.getName());
        row.setTitle(cv.getTitle());
        row.setSummary(cv.getSummary());
        row.setCvJson(JSON.toJSONString(cv));
        return row;
    }

    private CvBO fromRow(CareerResumeDO row) {
        CvBO cv = JSON.parseObject(row.getCvJson(), CvBO.class);
        if (cv == null) {
            cv = CvBO.builder().build();
        }
        return cv.toBuilder()
                .id(row.getId())
                .userId(row.getUserId())
                .cvType(defaultString(row.getCvType(), cv.getCvType()))
                .name(defaultString(row.getName(), cv.getName()))
                .title(defaultString(row.getTitle(), cv.getTitle()))
                .summary(defaultString(row.getSummary(), cv.getSummary()))
                .build();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
