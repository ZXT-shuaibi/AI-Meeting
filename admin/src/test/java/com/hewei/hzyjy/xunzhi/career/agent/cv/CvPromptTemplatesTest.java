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
    void agenticReviewerResourcesKeepDynamicJobProfileOutOfSystemPrompt() throws java.io.IOException {
        ClasspathCareerSkillRegistry registry = ClasspathCareerSkillRegistry.withBuiltIns();
        String systemPrompt = registry.find(CvPromptTemplates.REVIEWER_SKILL_NAME).orElseThrow().body();
        String userPrompt = new org.springframework.core.io.ClassPathResource(CvPromptTemplates.REVIEWER_USER_PROMPT_RESOURCE)
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(!systemPrompt.contains("{{jobDescription}}"));
        assertTrue(systemPrompt.contains("untrusted data"));
        assertTrue(userPrompt.contains("<job_profile>"));
        assertTrue(userPrompt.contains("{{jobDescription}}"));
    }

    @Test
    void reviewerSpringAiPromptsRenderSharedTemplates() {
        ClasspathCareerSkillRegistry registry = ClasspathCareerSkillRegistry.withBuiltIns();
        CvBO cv = CvBO.builder().summary("Java backend").build();

        String systemPrompt = CvPromptTemplates.reviewerSystemPrompt(registry, "Java engineer JD");
        String userPrompt = CvPromptTemplates.reviewerUserPrompt(cv, "Java engineer JD", List.of("template A"));

        assertTrue(!systemPrompt.contains("Java engineer JD"));
        assertTrue(systemPrompt.contains("<job_profile>"));
        assertTrue(userPrompt.contains("Java backend"));
        assertTrue(userPrompt.contains("template A"));
        assertTrue(userPrompt.contains("<job_profile>"));
        assertTrue(userPrompt.contains("<untrusted_resume>"));
        assertTrue(userPrompt.contains("<untrusted_evidence>"));
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

        assertTrue(!systemPrompt.contains("Java backend"));
        assertTrue(systemPrompt.contains("untrusted data"));
        assertTrue(systemPrompt.contains("岗位资料"));
        assertTrue(userPrompt.contains("Java backend"));
        assertTrue(userPrompt.contains("0.76"));
        assertTrue(userPrompt.contains("template B"));
        assertTrue(userPrompt.contains("<untrusted_resume>"));
        assertTrue(userPrompt.contains("<untrusted_review>"));
        assertTrue(userPrompt.contains("<untrusted_evidence>"));
    }

    @Test
    void tailorPromptCarriesJobProfileOnlyInUntrustedUserBoundary() {
        CvBO cv = CvBO.builder().summary("Java backend").build();

        String userPrompt = CvPromptTemplates.tailorUserPrompt(
                cv,
                "目标岗位：Java 后端工程师\n核心技能：Spring Boot、MySQL",
                new CvReview(0.76, "add stronger quantified impact"),
                List.of("template B")
        );

        assertTrue(userPrompt.contains("<job_profile>"));
        assertTrue(userPrompt.contains("核心技能：Spring Boot、MySQL"));
    }
}
