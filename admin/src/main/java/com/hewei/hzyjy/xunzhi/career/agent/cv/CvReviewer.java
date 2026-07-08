package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;

import java.util.List;

@FunctionalInterface
public interface CvReviewer {

    CvReview review(CvBO cv, String jobDescription, List<String> referenceTemplates);
}
