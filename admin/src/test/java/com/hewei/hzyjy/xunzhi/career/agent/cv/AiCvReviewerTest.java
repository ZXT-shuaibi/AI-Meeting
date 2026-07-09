package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.EducationBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.SkillBO;
import com.hewei.hzyjy.xunzhi.career.skill.CareerSkillRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
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
            public java.util.Optional<com.hewei.hzyjy.xunzhi.career.skill.CareerSkill> find(String name) {
                return java.util.Optional.empty();
            }

            @Override
            public String promptSection(String name) {
                if ("cv-reviewer".equals(name)) {
                    return """
                            Runtime Skill: cv-reviewer
                            四维评分体系：技术35/经验30/项目25/教育10
                            反馈结构：strengths/weaknesses/suggestions
                            """;
                }
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
        assertTrue(captured.get().systemPrompt().contains("Runtime Skill: cv-reviewer"));
        assertTrue(captured.get().systemPrompt().contains("技术35/经验30/项目25/教育10"));
        assertTrue(captured.get().systemPrompt().contains("strengths/weaknesses/suggestions"));
        assertTrue(captured.get().systemPrompt().contains("Return only JSON"));
        assertTrue(captured.get().userPrompt().contains("Reference templates"));
        assertTrue(review.feedback().contains("Need quantified results"));
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
