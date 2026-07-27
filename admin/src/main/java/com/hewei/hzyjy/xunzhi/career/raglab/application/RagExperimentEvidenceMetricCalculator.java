package com.hewei.hzyjy.xunzhi.career.raglab.application;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 只根据人工已确认的召回证据计算 Chunk 命中准确率。
 *
 * <p>基础岗位标签只能用于简历相关性真值，不能替代证据是否真正命中项目、技能或经历的判断。
 * 因此没有任何人工标注时返回 {@code null}，由页面明确提示“未标注”。</p>
 */
@Component
public class RagExperimentEvidenceMetricCalculator {

    public Double accuracy(List<Boolean> evidenceHits) {
        if (evidenceHits == null || evidenceHits.isEmpty()) {
            return null;
        }
        long valid = evidenceHits.stream().filter(Boolean.TRUE::equals).count();
        return valid / (double) evidenceHits.size();
    }
}
