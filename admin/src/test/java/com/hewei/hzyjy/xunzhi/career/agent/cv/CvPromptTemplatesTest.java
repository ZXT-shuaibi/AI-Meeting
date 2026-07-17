package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.skill.ClasspathCareerSkillRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CvPromptTemplatesTest {

    @Test
    void builtInRegistryBodiesMatchSharedPromptResources() {
        ClasspathCareerSkillRegistry registry = ClasspathCareerSkillRegistry.withBuiltIns();

        assertEquals(
                registry.find(CvPromptTemplates.REVIEWER_SKILL_NAME).orElseThrow().body(),
                CvPromptTemplates.reviewerSystemPrompt(registry)
        );
        assertEquals(
                registry.find(CvPromptTemplates.TAILOR_SKILL_NAME).orElseThrow().body(),
                CvPromptTemplates.tailorSystemPrompt(registry)
        );
    }

    @Test
    void reviewerSpringAiPromptsRenderSharedTemplates() {
        ClasspathCareerSkillRegistry registry = ClasspathCareerSkillRegistry.withBuiltIns();
        CvBO cv = CvBO.builder().summary("Java backend").build();

        String systemPrompt = CvPromptTemplates.reviewerSystemPrompt(registry, "Java engineer JD");
        String userPrompt = CvPromptTemplates.reviewerUserPrompt(cv, "Java engineer JD", List.of("template A"));

        assertTrue(systemPrompt.contains("Java engineer JD"));
        assertTrue(userPrompt.contains("Java backend"));
        assertTrue(userPrompt.contains("template A"));
        assertTrue(!userPrompt.contains("CvBO("));
    }

    @Test
    void tailorSpringAiPromptsRenderSharedTemplates() {
        ClasspathCareerSkillRegistry registry = ClasspathCareerSkillRegistry.withBuiltIns();
        CvBO cv = CvBO.builder().summary("Java backend").build();

        String systemPrompt = CvPromptTemplates.tailorSystemPrompt(registry, cv);
        String userPrompt = CvPromptTemplates.tailorUserPrompt(
                cv,
                new CvReview(0.76, "add stronger quantified impact"),
                List.of("template B")
        );

        assertTrue(systemPrompt.contains("Java backend"));
        assertTrue(userPrompt.contains("0.76"));
        assertTrue(userPrompt.contains("template B"));
    }
}
