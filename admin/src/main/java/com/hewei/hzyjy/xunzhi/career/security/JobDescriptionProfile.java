package com.hewei.hzyjy.xunzhi.career.security;

import java.util.List;

/** 经白名单校验后的岗位事实；下游检索和评分只消费此对象生成的查询。 */
public record JobDescriptionProfile(
        String jobTitle,
        List<String> directions,
        List<String> skills,
        List<String> responsibilities,
        Integer experienceYearsMin,
        String educationRequirement,
        List<String> industries,
        List<String> locations) {
    /**
     * 岗位名称加至少一项技能或职责才构成可用岗位画像。岗位方向可辅助检索，
     * 但不能单独作为召回、评分或改写依据。
     */
    public boolean usable() {
        return !blank(jobTitle) && hasRequiredSkillOrResponsibility(1);
    }

    public boolean hasRequiredSkillOrResponsibility(int minimum) {
        int required = Math.max(1, minimum);
        int count = (skills == null ? 0 : skills.size()) + (responsibilities == null ? 0 : responsibilities.size());
        return count >= required;
    }
    public String retrievalQuery() {
        StringBuilder value = new StringBuilder("目标岗位：").append(jobTitle == null ? "" : jobTitle);
        append(value, "岗位方向", directions); append(value, "核心技能", skills); append(value, "职责关键词", responsibilities);
        if (experienceYearsMin != null && experienceYearsMin > 0) value.append("\n经验要求：").append(experienceYearsMin).append(" 年");
        if (!blank(educationRequirement)) value.append("\n学历要求：").append(educationRequirement);
        append(value, "行业", industries); append(value, "地点", locations); return value.toString();
    }
    private static void append(StringBuilder builder, String label, List<String> values) { if (values != null && !values.isEmpty()) builder.append('\n').append(label).append('：').append(String.join("、", values)); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
