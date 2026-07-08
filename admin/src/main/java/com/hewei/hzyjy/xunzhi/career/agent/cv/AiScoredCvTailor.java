package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.alibaba.fastjson2.JSONObject;
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
public class AiScoredCvTailor implements ScoredCvTailor {

    private final AiGateway aiGateway;

    @Override
    public CvBO tailor(CvBO cv, CvReview review, List<String> referenceTemplates) {
        String response = aiGateway.chat(AiPromptRequest.builder()
                .sceneCode("RESUME_TAILOR")
                .systemPrompt("Rewrite resume wording based only on existing facts. Return JSON: {\"title\":\"...\",\"summary\":\"...\",\"advice\":\"...\"}. Do not invent experience.")
                .userPrompt("CV:\n" + cv + "\n\nReview:\n" + review + "\n\nReference templates:\n" + referenceTemplates)
                .build()).content();
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
}
