package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;

public interface ResumeStructuringService {

    CvBO structure(Long userId, String filename, String resumeText);
}