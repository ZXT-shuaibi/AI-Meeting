package com.hewei.hzyjy.xunzhi.career.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClasspathCareerSkillRegistryTest {

    @Test
    void loadsBuiltInJdAlignmentSkillWithReferences() {
        CareerSkillRegistry registry = ClasspathCareerSkillRegistry.withBuiltIns();

        CareerSkill skill = registry.find("jd-alignment").orElseThrow();

        assertTrue(skill.description().contains("岗位 JD"));
        assertTrue(skill.body().contains("JD 解析"));
        assertTrue(skill.references().get("jd-template.md").contains("MCP/A2A"));
        assertTrue(registry.promptSection("jd-alignment").contains("Runtime Skill: jd-alignment"));
    }

    @Test
    void missingSkillDegradesToEmptyPromptSection() {
        CareerSkillRegistry registry = ClasspathCareerSkillRegistry.withBuiltIns();

        assertFalse(registry.find("missing").isPresent());
        assertTrue(registry.promptSection("missing").isBlank());
    }

    @Test
    void loadsBuiltInCvReviewerAndTailorSkills() {
        CareerSkillRegistry registry = ClasspathCareerSkillRegistry.withBuiltIns();

        CareerSkill reviewer = registry.find("cv-reviewer").orElseThrow();
        CareerSkill tailor = registry.find("cv-tailor").orElseThrow();

        assertTrue(reviewer.body().contains("技术能力匹配度"));
        assertTrue(reviewer.body().contains("工作经验相关性"));
        assertTrue(reviewer.body().contains("strengths/weaknesses/suggestions"));
        assertTrue(reviewer.body().contains("参考模板"));
        assertTrue(registry.promptSection("cv-reviewer").contains("Runtime Skill: cv-reviewer"));

        assertTrue(tailor.body().contains("真实性底线"));
        assertTrue(tailor.body().contains("禁止虚构"));
        assertTrue(tailor.body().contains("技能/经验/项目/教育"));
        assertTrue(tailor.body().contains("输出格式规范"));
        assertTrue(registry.promptSection("cv-tailor").contains("Runtime Skill: cv-tailor"));
    }
}
