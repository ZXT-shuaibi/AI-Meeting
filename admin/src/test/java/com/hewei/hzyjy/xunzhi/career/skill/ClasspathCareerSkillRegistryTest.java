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
}
