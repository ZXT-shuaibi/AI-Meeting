package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;

import java.util.List;

@FunctionalInterface
public interface ScoredCvTailor {

    CvBO tailor(CvBO cv, CvReview review, List<String> referenceTemplates);
}
