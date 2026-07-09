package com.hewei.hzyjy.xunzhi.career.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClasspathCareerSkillRegistryTest {

    @Test
    void loadsBuiltInJdAlignmentSkillWithReferences() {
        CareerSkillRegistry registry = ClasspathCareerSkillRegistry.withBuiltIns();

        CareerSkill skill = registry.find("jd-alignment").orElseThrow();

        assertTrue(skill.references().containsKey("jd-template.md"));
        assertTrue(registry.promptSection("jd-alignment").contains("Runtime Skill: jd-alignment"));
    }

    @Test
    void missingSkillDegradesToEmptyPromptSection() {
        CareerSkillRegistry registry = ClasspathCareerSkillRegistry.withBuiltIns();

        assertFalse(registry.find("missing").isPresent());
        assertTrue(registry.promptSection("missing").isBlank());
    }

    @Test
    void loadsBuiltInCvReviewerAndTailorSystemPromptsFromDedicatedResources() {
        CareerSkillRegistry registry = ClasspathCareerSkillRegistry.withBuiltIns();

        CareerSkill reviewer = registry.find("cv-reviewer").orElseThrow();
        CareerSkill tailor = registry.find("cv-tailor").orElseThrow();

        assertTrue(reviewer.body().contains("{{jobDescription}}"));
        assertTrue(reviewer.body().contains("0.35"));
        assertTrue(tailor.body().contains("{{cv}}"));
        assertTrue(tailor.body().contains("LocaleConfig.sectionLabels"));
        assertTrue(tailor.body().contains("yyyy-MM-dd"));
        assertTrue(registry.promptSection("cv-reviewer").contains("Runtime Skill: cv-reviewer"));
        assertTrue(registry.promptSection("cv-tailor").contains("Runtime Skill: cv-tailor"));
    }
}
