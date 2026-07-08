package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.agent.support.AgentResponseParser;
import com.hewei.hzyjy.xunzhi.career.ai.AgentRuntimeGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
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
public class AiCvReviewer implements CvReviewer {

    private final AiGateway aiGateway;
    private final ObjectProvider<AgentRuntimeGateway> agentRuntimeGatewayProvider;

    @Autowired
    public AiCvReviewer(AiGateway aiGateway, ObjectProvider<AgentRuntimeGateway> agentRuntimeGatewayProvider) {
        this.aiGateway = aiGateway;
        this.agentRuntimeGatewayProvider = agentRuntimeGatewayProvider;
    }

    public AiCvReviewer(AiGateway aiGateway) {
        this.aiGateway = aiGateway;
        this.agentRuntimeGatewayProvider = null;
    }

    @Override
    public CvReview review(CvBO cv, String jobDescription, List<String> referenceTemplates) {
        String response = tryLangChain4j(cv, jobDescription, referenceTemplates);
        if (response == null || response.isBlank()) {
            response = aiGateway.chat(AiPromptRequest.builder()
                    .sceneCode("RESUME_REVIEW")
                    .systemPrompt("Review the resume against the JD. Return JSON: {\"score\":0.0-1.0,\"feedback\":\"concise diagnosis and rewrite advice\"}.")
                    .userPrompt("JD:\n" + jobDescription + "\n\nCV:\n" + cv + "\n\nReference templates:\n" + referenceTemplates)
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
        String cvText = String.valueOf(cv).toLowerCase();
        int hit = 0;
        int total = 0;
        for (String token : safe(jobDescription).toLowerCase().split("[^\\p{IsHan}\\p{Alnum}]+")) {
            if (token.length() < 2) {
                continue;
            }
            total++;
            if (cvText.contains(token)) {
                hit++;
            }
        }
        if (total == 0) {
            return 0.5;
        }
        return Math.min(0.95, 0.45 + 0.5 * hit / total);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
