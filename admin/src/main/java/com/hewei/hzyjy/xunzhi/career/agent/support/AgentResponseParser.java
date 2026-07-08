package com.hewei.hzyjy.xunzhi.career.agent.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentResponseParser {

    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*}");
    private static final Pattern SCORE_PATTERN = Pattern.compile("(?i)(?:score|rating)\\s*[:=]\\s*(10|[0-9](?:\\.\\d+)?|0?\\.\\d+|[1-9][0-9])");
    private static final Pattern DECISION_PATTERN = Pattern.compile("(?i)\\b(PROBE|NEXT|STAGE_FINISH|FINISH)\\b");

    private AgentResponseParser() {
    }

    public static Optional<JSONObject> jsonObject(String response) {
        if (response == null || response.isBlank()) {
            return Optional.empty();
        }
        String candidate = response.trim();
        if (!candidate.startsWith("{")) {
            Matcher matcher = JSON_OBJECT.matcher(response);
            if (!matcher.find()) {
                return Optional.empty();
            }
            candidate = matcher.group();
        }
        try {
            return Optional.ofNullable(JSON.parseObject(candidate));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public static Optional<Double> score(String response) {
        Optional<JSONObject> json = jsonObject(response);
        if (json.isPresent()) {
            Double score = json.get().getDouble("score");
            if (score == null) {
                score = json.get().getDouble("matchScore");
            }
            if (score != null) {
                return Optional.of(normalizeScore(score));
            }
        }
        if (response == null) {
            return Optional.empty();
        }
        Matcher matcher = SCORE_PATTERN.matcher(response);
        if (matcher.find()) {
            try {
                return Optional.of(normalizeScore(Double.parseDouble(matcher.group(1))));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static Optional<String> feedback(String response) {
        Optional<JSONObject> json = jsonObject(response);
        if (json.isPresent()) {
            String feedback = firstNonBlank(
                    json.get().getString("feedback"),
                    json.get().getString("summary"),
                    json.get().getString("advice")
            );
            if (feedback != null) {
                return Optional.of(feedback);
            }
        }
        return response == null || response.isBlank() ? Optional.empty() : Optional.of(response.trim());
    }

    public static Optional<String> decision(String response) {
        Optional<JSONObject> json = jsonObject(response);
        if (json.isPresent()) {
            String decision = json.get().getString("decision");
            if (decision != null && !decision.isBlank()) {
                return Optional.of(decision.trim().toUpperCase());
            }
        }
        if (response == null) {
            return Optional.empty();
        }
        Matcher matcher = DECISION_PATTERN.matcher(response);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).toUpperCase());
        }
        return Optional.empty();
    }

    private static double normalizeScore(double score) {
        double normalized = score > 1.0 ? score / 10.0 : score;
        return Math.max(0.0, Math.min(1.0, normalized));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
