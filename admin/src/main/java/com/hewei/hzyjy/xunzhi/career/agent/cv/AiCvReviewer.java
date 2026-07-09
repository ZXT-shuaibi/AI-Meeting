package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.agent.support.AgentResponseParser;
import com.hewei.hzyjy.xunzhi.career.ai.AgentRuntimeGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.skill.CareerSkillRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Primary
@Component
public class AiCvReviewer implements CvReviewer {

    private final AiGateway aiGateway;
    private final ObjectProvider<AgentRuntimeGateway> agentRuntimeGatewayProvider;
    private final CareerSkillRegistry skillRegistry;

    @Autowired
    public AiCvReviewer(
            AiGateway aiGateway,
            ObjectProvider<AgentRuntimeGateway> agentRuntimeGatewayProvider,
            ObjectProvider<CareerSkillRegistry> skillRegistryProvider) {
        this.aiGateway = aiGateway;
        this.agentRuntimeGatewayProvider = agentRuntimeGatewayProvider;
        this.skillRegistry = skillRegistryProvider.getIfAvailable(CareerSkillRegistry::disabled);
    }

    public AiCvReviewer(AiGateway aiGateway) {
        this(aiGateway, CareerSkillRegistry.disabled());
    }

    public AiCvReviewer(AiGateway aiGateway, CareerSkillRegistry skillRegistry) {
        this.aiGateway = aiGateway;
        this.agentRuntimeGatewayProvider = null;
        this.skillRegistry = skillRegistry == null ? CareerSkillRegistry.disabled() : skillRegistry;
    }

    @Override
    public CvReview review(CvBO cv, String jobDescription, List<String> referenceTemplates) {
        String response = tryLangChain4j(cv, jobDescription, referenceTemplates);
        if (response == null || response.isBlank()) {
            response = aiGateway.chat(AiPromptRequest.builder()
                    .sceneCode("RESUME_REVIEW")
                    .systemPrompt(buildSystemPrompt())
                    .userPrompt(CvPromptTemplates.REVIEWER_USER_PROMPT.formatted(jobDescription, cv, referenceTemplates))
                    .build()).content();
        }
        double score = AgentResponseParser.score(response).orElseGet(() -> heuristicScore(cv, jobDescription));
        String feedback = AgentResponseParser.feedback(response).orElse(response);
        return new CvReview(score, feedback);
    }

    private String tryLangChain4j(CvBO cv, String jobDescription, List<String> referenceTemplates) {
        AgentRuntimeGateway gateway = agentRuntimeGatewayProvider == null ? null : agentRuntimeGatewayProvider.getIfAvailable();
        if (gateway == null) {
            return null;
        }
        try {
            return gateway.invoke("CvReviewer", "review", Map.of(
                    "cv", cv,
                    "jobDescription", jobDescription,
                    "referenceTemplates", referenceTemplates == null ? List.of() : referenceTemplates
            ), String.class);
        } catch (Exception ex) {
            log.debug("LangChain4j CvReviewer unavailable, falling back to Spring AI", ex);
            return null;
        }
    }

    private double heuristicScore(CvBO cv, String jobDescription) {
        List<String> tokens = extractMeaningfulTokens(jobDescription);
        if (tokens.isEmpty()) {
            return 0.5;
        }
        String technicalText = String.join(" ",
                safe(cv == null ? null : cv.getTitle()),
                safe(cv == null ? null : cv.getSummary()),
                stringify(cv == null ? null : cv.getSkills()));
        String experienceText = String.join(" ",
                safe(cv == null ? null : cv.getSummary()),
                stringify(cv == null ? null : cv.getExperiences()));
        String projectText = String.join(" ",
                safe(cv == null ? null : cv.getSummary()),
                stringify(cv == null ? null : cv.getProjects()));
        String educationText = String.join(" ",
                stringify(cv == null ? null : cv.getEducations()),
                stringify(cv == null ? null : cv.getCertificates()));

        double technical = coverage(tokens, technicalText);
        double experience = coverage(tokens, experienceText);
        double project = coverage(tokens, projectText);
        double education = coverage(tokens, educationText);
        double weightedScore = technical * 0.35 + experience * 0.30 + project * 0.25 + education * 0.10;
        return Math.min(0.95, 0.35 + 0.6 * weightedScore);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<String> extractMeaningfulTokens(String text) {
        List<String> tokens = new ArrayList<>();
        for (String token : safe(text).toLowerCase().split("[^\\p{IsHan}\\p{Alnum}]+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private double coverage(List<String> tokens, String sectionText) {
        if (tokens == null || tokens.isEmpty()) {
            return 0.0;
        }
        String normalizedSection = safe(sectionText).toLowerCase();
        int hit = 0;
        for (String token : tokens) {
            if (normalizedSection.contains(token)) {
                hit++;
            }
        }
        return (double) hit / tokens.size();
    }

    private String buildSystemPrompt() {
        String prompt = skillRegistry == null ? "" : skillRegistry.promptSection(CvPromptTemplates.REVIEWER_SKILL_NAME);
        if (prompt.isBlank()) {
            return CvPromptTemplates.REVIEWER_BASE_PROMPT;
        }
        return CvPromptTemplates.REVIEWER_BASE_PROMPT + "\n\n" + prompt;
    }
}
