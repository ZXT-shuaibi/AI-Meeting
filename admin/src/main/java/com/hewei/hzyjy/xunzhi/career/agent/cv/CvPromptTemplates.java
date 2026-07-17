package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.skill.CareerSkill;
import com.hewei.hzyjy.xunzhi.career.skill.CareerSkillRegistry;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

final class CvPromptTemplates {

    static final String REVIEWER_SKILL_NAME = "cv-reviewer";
    static final String TAILOR_SKILL_NAME = "cv-tailor";

    static final String REVIEWER_SYSTEM_PROMPT_RESOURCE = "career-skills/cv-reviewer/system-prompt.md";
    static final String REVIEWER_USER_PROMPT_RESOURCE = "career-skills/cv-reviewer/user-prompt.md";
    static final String TAILOR_SYSTEM_PROMPT_RESOURCE = "career-skills/cv-tailor/system-prompt.md";
    static final String TAILOR_USER_PROMPT_RESOURCE = "career-skills/cv-tailor/user-prompt.md";

    private static final String REVIEWER_SYSTEM_PROMPT = readClasspathResource(REVIEWER_SYSTEM_PROMPT_RESOURCE);
    private static final String REVIEWER_USER_PROMPT_TEMPLATE = readClasspathResource(REVIEWER_USER_PROMPT_RESOURCE);
    private static final String TAILOR_SYSTEM_PROMPT = readClasspathResource(TAILOR_SYSTEM_PROMPT_RESOURCE);
    private static final String TAILOR_USER_PROMPT_TEMPLATE = readClasspathResource(TAILOR_USER_PROMPT_RESOURCE);

    private CvPromptTemplates() {
    }

    static String reviewerSystemPrompt(CareerSkillRegistry registry) {
        return skillBodyOrFallback(registry, REVIEWER_SKILL_NAME, REVIEWER_SYSTEM_PROMPT);
    }

    static String reviewerSystemPrompt(CareerSkillRegistry registry, String jobDescription) {
        return render(reviewerSystemPrompt(registry), Map.of(
                "jobDescription", safe(jobDescription)
        ));
    }

    static String tailorSystemPrompt(CareerSkillRegistry registry) {
        return skillBodyOrFallback(registry, TAILOR_SKILL_NAME, TAILOR_SYSTEM_PROMPT);
    }

    static String tailorSystemPrompt(CareerSkillRegistry registry, CvBO cv) {
        return render(tailorSystemPrompt(registry), Map.of(
                "cv", cvText(cv)
        ));
    }

    static String reviewerUserPrompt(CvBO cv, String jobDescription, List<String> referenceTemplates) {
        return render(REVIEWER_USER_PROMPT_TEMPLATE, Map.of(
                "cv", cvText(cv),
                "jobDescription", safe(jobDescription),
                "referenceTemplates", stringify(referenceTemplates == null ? List.of() : referenceTemplates)
        ));
    }

    static String tailorUserPrompt(CvBO cv, CvReview review, List<String> referenceTemplates) {
        return render(TAILOR_USER_PROMPT_TEMPLATE, Map.of(
                "cv", cvText(cv),
                "cvReview", stringify(review),
                "referenceTemplates", stringify(referenceTemplates == null ? List.of() : referenceTemplates)
        ));
    }

    private static String skillBodyOrFallback(CareerSkillRegistry registry, String skillName, String fallback) {
        if (registry == null) {
            return fallback;
        }
        return registry.find(skillName)
                .map(CareerSkill::body)
                .filter(body -> body != null && !body.isBlank())
                .orElse(fallback);
    }

    private static String render(String template, Map<String, String> values) {
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", safe(entry.getValue()));
        }
        return rendered;
    }

    private static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String cvText(CvBO cv) {
        if (cv == null) {
            return "";
        }
        return "Name: " + safe(cv.getName()) + "\n"
                + "Target title: " + safe(cv.getTitle()) + "\n"
                + "Summary:\n" + safe(cv.getSummary()) + "\n"
                + "Skills: " + stringify(cv.getSkills()) + "\n"
                + "Experience: " + stringify(cv.getExperiences()) + "\n"
                + "Projects: " + stringify(cv.getProjects()) + "\n"
                + "Education: " + stringify(cv.getEducations());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String readClasspathResource(String resourcePath) {
        try {
            return new ClassPathResource(resourcePath).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read CV prompt resource: " + resourcePath, ex);
        }
    }
}
