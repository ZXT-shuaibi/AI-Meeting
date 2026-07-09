package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.EducationBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.SkillBO;
import com.hewei.hzyjy.xunzhi.career.skill.CareerSkill;
import com.hewei.hzyjy.xunzhi.career.skill.CareerSkillRegistry;
import com.hewei.hzyjy.xunzhi.career.skill.ClasspathCareerSkillRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCvReviewerTest {

    @Test
    void usesStructuredLlmScoreBeforeHeuristicFallback() {
        AiCvReviewer reviewer = new AiCvReviewer(request -> AiGatewayResult.builder()
                .content("{\"score\":0.86,\"feedback\":\"Strong JD fit, add metrics.\"}")
                .provider("test")
                .build(), CareerSkillRegistry.disabled());

        CvReview review = reviewer.review(CvBO.builder().summary("java redis").build(), "python frontend", List.of());

        assertEquals(0.86, review.score());
        assertTrue(review.feedback().contains("Strong JD fit"));
    }

    @Test
    void injectsReviewerSkillPromptIntoSpringAiFallback() {
        AtomicReference<AiPromptRequest> captured = new AtomicReference<>();
        CareerSkillRegistry registry = new CareerSkillRegistry() {
            @Override
            public Optional<CareerSkill> find(String name) {
                if (!"cv-reviewer".equals(name)) {
                    return Optional.empty();
                }
                return Optional.of(new CareerSkill(
                        "cv-reviewer",
                        "custom reviewer prompt",
                        """
                                Reviewer system prompt from registry
                                技术35/经验30/项目25/教育10
                                strengths/weaknesses/suggestions
                                """,
                        Map.of()
                ));
            }

            @Override
            public String promptSection(String name) {
                return "";
            }
        };
        AiCvReviewer reviewer = new AiCvReviewer(request -> {
            captured.set(request);
            return AiGatewayResult.builder()
                    .content("{\"score\":0.62,\"feedback\":\"Need quantified results.\"}")
                    .provider("test")
                    .build();
        }, registry);

        CvReview review = reviewer.review(CvBO.builder().summary("java backend").build(), "Java backend engineer", List.of("template"));

        assertEquals(0.62, review.score());
        assertTrue(captured.get().systemPrompt().contains("Reviewer system prompt from registry"));
        assertTrue(captured.get().systemPrompt().contains("技术35/经验30/项目25/教育10"));
        assertTrue(captured.get().systemPrompt().contains("strengths/weaknesses/suggestions"));
        assertTrue(captured.get().userPrompt().contains("java backend"));
        assertTrue(captured.get().userPrompt().contains("template"));
        assertTrue(review.feedback().contains("Need quantified results"));
    }

    @Test
    void usesSharedReviewerPromptResourceWhenBuiltInRegistryIsAvailable() {
        AtomicReference<AiPromptRequest> captured = new AtomicReference<>();
        CareerSkillRegistry registry = ClasspathCareerSkillRegistry.withBuiltIns();
        AiCvReviewer reviewer = new AiCvReviewer(request -> {
            captured.set(request);
            return AiGatewayResult.builder()
                    .content("{\"score\":0.81,\"feedback\":\"Solid fit.\"}")
                    .provider("test")
                    .build();
        }, registry);

        reviewer.review(CvBO.builder().summary("java backend").build(), "Java backend engineer", List.of("template"));

        assertEquals(CvPromptTemplates.reviewerSystemPrompt(registry, "Java backend engineer"), captured.get().systemPrompt());
        assertTrue(captured.get().systemPrompt().contains("Java backend engineer"));
        assertTrue(captured.get().systemPrompt().contains("0.35"));
    }

    @Test
    void heuristicFallbackWeightsTechnicalSignalsMoreThanEducationSignals() {
        AiCvReviewer reviewer = new AiCvReviewer(request -> AiGatewayResult.builder()
                .content("not-json")
                .provider("test")
                .build(), CareerSkillRegistry.disabled());

        CvBO technicalCv = CvBO.builder()
                .summary("backend engineer")
                .skills(List.of(
                        SkillBO.builder().name("java").level("advanced").build(),
                        SkillBO.builder().name("spring").level("advanced").build()
                ))
                .build();
        CvBO educationCv = CvBO.builder()
                .summary("backend engineer")
                .educations(List.of(
                        EducationBO.builder().degree("Bachelor").major("Computer Science").description("software engineering").build()
                ))
                .build();

        CvReview technicalReview = reviewer.review(technicalCv, "Java Spring backend bachelor", List.of());
        CvReview educationReview = reviewer.review(educationCv, "Java Spring backend bachelor", List.of());

        assertTrue(technicalReview.score() > educationReview.score());
    }
}
