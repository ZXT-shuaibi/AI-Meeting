package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.alibaba.fastjson2.JSONObject;
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

import java.util.List;
import java.util.Map;

@Slf4j
@Primary
@Component
public class AiScoredCvTailor implements ScoredCvTailor {

    private final AiGateway aiGateway;
    private final ObjectProvider<AgentRuntimeGateway> agentRuntimeGatewayProvider;
    private final CareerSkillRegistry skillRegistry;

    @Autowired
    public AiScoredCvTailor(
            AiGateway aiGateway,
            ObjectProvider<AgentRuntimeGateway> agentRuntimeGatewayProvider,
            ObjectProvider<CareerSkillRegistry> skillRegistryProvider) {
        this.aiGateway = aiGateway;
        this.agentRuntimeGatewayProvider = agentRuntimeGatewayProvider;
        this.skillRegistry = skillRegistryProvider.getIfAvailable(CareerSkillRegistry::disabled);
    }

    public AiScoredCvTailor(AiGateway aiGateway) {
        this(aiGateway, CareerSkillRegistry.disabled());
    }

    public AiScoredCvTailor(AiGateway aiGateway, CareerSkillRegistry skillRegistry) {
        this.aiGateway = aiGateway;
        this.agentRuntimeGatewayProvider = null;
        this.skillRegistry = skillRegistry == null ? CareerSkillRegistry.disabled() : skillRegistry;
    }

    @Override
    public CvBO tailor(CvBO cv, CvReview review, List<String> referenceTemplates) {
        String response = tryLangChain4j(cv, review, referenceTemplates);
        if (response == null || response.isBlank()) {
            response = aiGateway.chat(AiPromptRequest.builder()
                    .sceneCode("RESUME_TAILOR")
                    .systemPrompt(buildSystemPrompt(cv))
                    .userPrompt(CvPromptTemplates.tailorUserPrompt(cv, review, referenceTemplates))
                    .build()).content();
        }
        JSONObject json = AgentResponseParser.jsonObject(response).orElse(null);
        String title = json == null ? null : json.getString("title");
        String summary = json == null ? null : json.getString("summary");
        String advice = json == null ? response : firstNonBlank(json.getString("advice"), json.getString("feedback"), response);
        String mergedAdvice = review.feedback() + "\n\nTailor advice:\n" + advice;
        return cv.toBuilder()
                .title(firstNonBlank(title, cv.getTitle()))
                .advice(mergedAdvice)
                .summary(firstNonBlank(summary, mergeSummary(cv.getSummary(), review.feedback())))
                .build();
    }

    private String tryLangChain4j(CvBO cv, CvReview review, List<String> referenceTemplates) {
        AgentRuntimeGateway gateway = agentRuntimeGatewayProvider == null ? null : agentRuntimeGatewayProvider.getIfAvailable();
        if (gateway == null) {
            return null;
        }
        try {
            return gateway.invoke("ScoredCvTailor", "tailor", Map.of(
                    "cv", cv,
                    "cvReview", review,
                    "referenceTemplates", referenceTemplates == null ? List.of() : referenceTemplates
            ), String.class);
        } catch (Exception ex) {
            log.debug("LangChain4j ScoredCvTailor unavailable, falling back to Spring AI", ex);
            return null;
        }
    }

    private String mergeSummary(String summary, String feedback) {
        String safeSummary = summary == null ? "" : summary;
        if (safeSummary.contains("Optimized for target JD")) {
            return safeSummary;
        }
        return safeSummary + "\nOptimized for target JD: " + abbreviate(feedback, 180);
    }

    private String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String buildSystemPrompt(CvBO cv) {
        return CvPromptTemplates.tailorSystemPrompt(skillRegistry, cv);
    }
}
