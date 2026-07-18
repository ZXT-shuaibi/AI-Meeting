package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.alibaba.fastjson2.JSONObject;
import com.hewei.hzyjy.xunzhi.career.agent.support.AgentResponseParser;

import java.util.List;

public record CvReview(
        double score,
        String feedback,
        String summary,
        List<String> strengths,
        List<String> weaknesses,
        List<String> suggestions
) {

    public CvReview(double score, String feedback) {
        this(score, feedback, null, List.of(), List.of(), List.of());
    }

    public CvReview {
        feedback = normalizeText(feedback);
        summary = firstNonBlank(summary, feedback);
        strengths = normalizeItems(strengths);
        weaknesses = normalizeItems(weaknesses);
        suggestions = normalizeItems(suggestions);
    }

    /**
     * Preserves the original feedback text for downstream tailoring while exposing
     * model-provided sections for a stable resume-review UI.
     */
    public static CvReview fromModelResponse(double score, String response) {
        String feedback = AgentResponseParser.feedback(response).orElse(response);
        JSONObject payload = AgentResponseParser.jsonObject(response).orElse(null);
        if (payload == null) {
            return new CvReview(score, feedback);
        }
        return new CvReview(
                score,
                feedback,
                payload.getString("summary"),
                payload.getList("strengths", String.class),
                payload.getList("weaknesses", String.class),
                payload.getList("suggestions", String.class)
        );
    }

    private static List<String> normalizeItems(List<String> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(CvReview::normalizeText)
                .filter(item -> item != null && !item.isBlank())
                .toList();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        String normalized = normalizeText(preferred);
        return normalized == null || normalized.isBlank() ? normalizeText(fallback) : normalized;
    }

    private static String normalizeText(String value) {
        return value == null ? null : value.trim();
    }
}
