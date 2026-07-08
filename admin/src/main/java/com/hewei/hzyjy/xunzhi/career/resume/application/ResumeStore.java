package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;

import java.util.Optional;

public interface ResumeStore {

    CvBO save(CvBO cv);

    Optional<CvBO> findById(Long resumeId);
}
