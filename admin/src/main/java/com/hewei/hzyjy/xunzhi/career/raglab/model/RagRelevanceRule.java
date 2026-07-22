package com.hewei.hzyjy.xunzhi.career.raglab.model;

/**
 * 实验提交时冻结的一条标签判定规则。
 * 标签内容保持自由输入，但规则分值严格限制为 0 / 1 / 3，分别表示不相关、一般相关和强相关。
 */
public record RagRelevanceRule(RagResumeTagType tagType, String tagValue, int score) {

    public RagRelevanceRule {
        if (tagType == null) {
            throw new IllegalArgumentException("标签类型不能为空");
        }
        tagValue = tagValue == null ? "" : tagValue.trim();
        if (tagValue.isBlank() || tagValue.length() > 100) {
            throw new IllegalArgumentException("标签内容不能为空且不能超过 100 个字符");
        }
        if (score != 0 && score != 1 && score != 3) {
            throw new IllegalArgumentException("相关性评分只允许 0、1 或 3");
        }
    }
}
