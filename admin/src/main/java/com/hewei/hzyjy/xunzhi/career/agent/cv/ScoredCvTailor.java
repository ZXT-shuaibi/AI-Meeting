package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;

import java.util.List;

@FunctionalInterface
public interface ScoredCvTailor {

    CvBO tailor(CvBO cv, CvReview review, List<String> referenceTemplates);

    /**
     * 岗位上下文由优化编排器统一传入。保留三参数抽象方法，避免已有实现和 lambda 适配器失效；
     * 所有需要岗位信息的实现应覆盖本方法，不能再从评分反馈反向猜测目标岗位。
     */
    default CvBO tailor(CvBO cv, String jobProfile, CvReview review, List<String> referenceTemplates) {
        return tailor(cv, review, referenceTemplates);
    }
}
