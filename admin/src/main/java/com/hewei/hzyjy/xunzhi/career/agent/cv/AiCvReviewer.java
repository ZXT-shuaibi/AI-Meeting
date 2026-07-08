package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.agent.support.AgentResponseParser;
import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Primary
@Component
@RequiredArgsConstructor
public class AiCvReviewer implements CvReviewer {

    private final AiGateway aiGateway;

    @Override
    public CvReview review(CvBO cv, String jobDescription, List<String> referenceTemplates) {
        String response = aiGateway.chat(AiPromptRequest.builder()
                .sceneCode("RESUME_REVIEW")
                .systemPrompt("Review the resume against the JD. Return JSON: {\"score\":0.0-1.0,\"feedback\":\"concise diagnosis and rewrite advice\"}.")
                .userPrompt("JD:\n" + jobDescription + "\n\nCV:\n" + cv + "\n\nReference templates:\n" + referenceTemplates)
                .build()).content();
        double score = AgentResponseParser.score(response).orElseGet(() -> heuristicScore(cv, jobDescription));
        String feedback = AgentResponseParser.feedback(response).orElse(response);
        return new CvReview(score, feedback);
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
