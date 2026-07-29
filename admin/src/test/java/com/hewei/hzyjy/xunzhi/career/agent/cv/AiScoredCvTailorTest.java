package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import com.hewei.hzyjy.xunzhi.career.resume.model.ContactBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.ExperienceBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.ProjectBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.SkillBO;
import com.hewei.hzyjy.xunzhi.career.skill.CareerSkill;
import com.hewei.hzyjy.xunzhi.career.skill.CareerSkillRegistry;
import com.hewei.hzyjy.xunzhi.career.skill.ClasspathCareerSkillRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiScoredCvTailorTest {

    @Test
    void exposesExactlyOneAutowiredConstructorForSpringStartup() {
        long autowiredConstructors = java.util.Arrays.stream(AiScoredCvTailor.class.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .count();

        assertEquals(1L, autowiredConstructors);
    }

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

    @Test
    void rendersSafeJobProfileInTailorUserPromptInsteadOfSystemPrompt() {
        AtomicReference<AiPromptRequest> captured = new AtomicReference<>();
        AiScoredCvTailor tailor = new AiScoredCvTailor(request -> {
            captured.set(request);
            return AiGatewayResult.builder()
                    .content("{\"title\":\"Java Backend Engineer\",\"summary\":\"Tailored summary\"}")
                    .provider("test")
                    .build();
        });

        tailor.tailor(
                CvBO.builder().summary("java backend").build(),
                "目标岗位：Java 后端工程师\n核心技能：Spring Boot、MySQL",
                new CvReview(0.78, "Add stronger metrics."),
                List.of("template")
        );

        assertTrue(captured.get().userPrompt().contains("<job_profile>"));
        assertTrue(captured.get().userPrompt().contains("Spring Boot"));
        assertTrue(captured.get().userPrompt().contains("MySQL"));
        assertTrue(!captured.get().systemPrompt().contains("Spring Boot"));
    }

    @Test
    void appliesFullCvStructureWhenSpringAiReturnsWholeCvJson() {
        CvBO original = CvBO.builder()
                .title("Old title")
                .summary("Old summary")
                .advice("Old advice")
                .contact(ContactBO.builder().email("old@example.com").build())
                .skills(List.of(SkillBO.builder().name("Java").level("experienced").build()))
                .experiences(List.of(ExperienceBO.builder().company("Old Co").role("Engineer").description("legacy").build()))
                .projects(List.of(ProjectBO.builder().name("Legacy Project").description("legacy").build()))
                .build();
        AiScoredCvTailor tailor = new AiScoredCvTailor(request -> AiGatewayResult.builder()
                .content("""
                        {
                          "title":"Java Backend Engineer",
                          "summary":"Tailored summary",
                          "advice":"Keep impact metrics.",
                          "contact":{"email":"new@example.com"},
                          "skills":[{"name":"Spring Boot","level":"advanced"}],
                          "experiences":[{"company":"New Co","role":"Senior Engineer","description":"built platform"}],
                          "projects":[{"name":"AI Resume","description":"rebuilt optimization chain"}]
                        }
                        """)
                .provider("test")
                .build());

        CvBO tailored = tailor.tailor(original, new CvReview(0.82, "Add stronger metrics."), List.of("template"));

        assertEquals("Java Backend Engineer", tailored.getTitle());
        assertEquals("Tailored summary", tailored.getSummary());
        assertEquals("new@example.com", tailored.getContact().getEmail());
        assertEquals(1, tailored.getSkills().size());
        assertEquals("Spring Boot", tailored.getSkills().get(0).getName());
        assertEquals("New Co", tailored.getExperiences().get(0).getCompany());
        assertEquals("AI Resume", tailored.getProjects().get(0).getName());
        assertTrue(tailored.getAdvice().contains("Add stronger metrics."));
        assertTrue(tailored.getAdvice().contains("Keep impact metrics."));
    }

    @Test
    void prefersStructuredLangChain4jCvWhenGatewayReturnsCvObject() {
        CvBO original = CvBO.builder()
                .title("Old title")
                .summary("Old summary")
                .build();
        CvBO gatewayCv = CvBO.builder()
                .title("Gateway title")
                .summary("Gateway summary")
                .contact(ContactBO.builder().email("gateway@example.com").build())
                .skills(List.of(SkillBO.builder().name("LangChain4j").level("advanced").build()))
                .build();
        AiScoredCvTailor tailor = new AiScoredCvTailor(
                request -> {
                    throw new AssertionError("Spring AI fallback should not be used when gateway returns CvBO");
                },
                new org.springframework.beans.factory.ObjectProvider<>() {
                    @Override
                    public com.hewei.hzyjy.xunzhi.career.ai.AgentRuntimeGateway getObject(Object... args) {
                        return getObject();
                    }

                    @Override
                    public com.hewei.hzyjy.xunzhi.career.ai.AgentRuntimeGateway getIfAvailable() {
                        return getObject();
                    }

                    @Override
                    public com.hewei.hzyjy.xunzhi.career.ai.AgentRuntimeGateway getIfUnique() {
                        return getObject();
                    }

                    @Override
                    public com.hewei.hzyjy.xunzhi.career.ai.AgentRuntimeGateway getObject() {
                        return new com.hewei.hzyjy.xunzhi.career.ai.AgentRuntimeGateway() {
                            @Override
                            public <T> T invoke(String agentName, String methodName, Map<String, Object> variables, Class<T> responseType) {
                                return responseType.cast(gatewayCv);
                            }
                        };
                    }
                },
                new org.springframework.beans.factory.ObjectProvider<>() {
                    @Override
                    public CareerSkillRegistry getObject(Object... args) {
                        return CareerSkillRegistry.disabled();
                    }

                    @Override
                    public CareerSkillRegistry getIfAvailable() {
                        return CareerSkillRegistry.disabled();
                    }

                    @Override
                    public CareerSkillRegistry getIfUnique() {
                        return CareerSkillRegistry.disabled();
                    }

                    @Override
                    public CareerSkillRegistry getObject() {
                        return CareerSkillRegistry.disabled();
                    }
                });

        CvBO tailored = tailor.tailor(original, new CvReview(0.9, "Great match."), List.of());

        assertEquals("Gateway title", tailored.getTitle());
        assertEquals("Gateway summary", tailored.getSummary());
        assertNotNull(tailored.getContact());
        assertEquals("gateway@example.com", tailored.getContact().getEmail());
        assertEquals("LangChain4j", tailored.getSkills().get(0).getName());
        assertTrue(tailored.getAdvice().contains("Great match."));
    }
}
