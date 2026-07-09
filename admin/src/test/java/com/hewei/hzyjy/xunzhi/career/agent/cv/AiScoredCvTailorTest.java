package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
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

class AiScoredCvTailorTest {

    @Test
    void injectsTailorSkillPromptIntoSpringAiFallback() {
        AtomicReference<AiPromptRequest> captured = new AtomicReference<>();
        CareerSkillRegistry registry = new CareerSkillRegistry() {
            @Override
            public Optional<CareerSkill> find(String name) {
                if (!"cv-tailor".equals(name)) {
                    return Optional.empty();
                }
                return Optional.of(new CareerSkill(
                        "cv-tailor",
                        "custom tailor prompt",
                        """
                                Tailor system prompt from registry
                                绝对禁止虚构
                                LocaleConfig.sectionLabels
                                """,
                        Map.of()
                ));
            }

            @Override
            public String promptSection(String name) {
                return "";
            }
        };
        AiScoredCvTailor tailor = new AiScoredCvTailor(request -> {
            captured.set(request);
            return AiGatewayResult.builder()
                    .content("{\"title\":\"Java Backend Engineer\",\"summary\":\"Tailored summary\",\"advice\":\"Keep impact metrics.\"}")
                    .provider("test")
                    .build();
        }, registry);

        tailor.tailor(CvBO.builder().summary("java backend").build(), new CvReview(0.78, "Add stronger metrics."), List.of("template"));

        assertTrue(captured.get().systemPrompt().contains("Tailor system prompt from registry"));
        assertTrue(captured.get().systemPrompt().contains("绝对禁止虚构"));
        assertTrue(captured.get().userPrompt().contains("template"));
    }

    @Test
    void usesSharedTailorPromptResourceWhenBuiltInRegistryIsAvailable() {
        AtomicReference<AiPromptRequest> captured = new AtomicReference<>();
        CareerSkillRegistry registry = ClasspathCareerSkillRegistry.withBuiltIns();
        CvBO cv = CvBO.builder().summary("java backend").build();
        AiScoredCvTailor tailor = new AiScoredCvTailor(request -> {
            captured.set(request);
            return AiGatewayResult.builder()
                    .content("{\"title\":\"Java Backend Engineer\",\"summary\":\"Tailored summary\",\"advice\":\"Keep impact metrics.\"}")
                    .provider("test")
                    .build();
        }, registry);

        tailor.tailor(cv, new CvReview(0.78, "Add stronger metrics."), List.of("template"));

        assertEquals(CvPromptTemplates.tailorSystemPrompt(registry, cv), captured.get().systemPrompt());
        assertTrue(captured.get().systemPrompt().contains("CvBO"));
        assertTrue(captured.get().systemPrompt().contains("LocaleConfig.sectionLabels"));
        assertTrue(captured.get().systemPrompt().contains("yyyy-MM-dd"));
    }
}
