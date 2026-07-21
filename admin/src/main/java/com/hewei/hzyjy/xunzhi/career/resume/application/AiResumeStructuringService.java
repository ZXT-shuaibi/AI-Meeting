package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hewei.hzyjy.xunzhi.career.agent.support.AgentResponseParser;
import com.hewei.hzyjy.xunzhi.career.agent.support.CareerJsonResponseCleaner;
import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiResumeStructuringService implements ResumeStructuringService {

    private static final int MAX_STRUCTURING_TEXT_LENGTH = 24000;

    private final AiGateway aiGateway;

    @Override
    public CvBO structure(Long userId, String filename, String resumeText) {
        if (!StringUtils.hasText(resumeText)) {
            return null;
        }
        try {
            String content = aiGateway.chat(AiPromptRequest.builder()
                    .sceneCode("RESUME_ANALYSIS")
                    .systemPrompt(systemPrompt())
                    .userPrompt(abbreviate(resumeText, MAX_STRUCTURING_TEXT_LENGTH))
                    .metadata(Map.of(
                            "userId", userId == null ? "" : String.valueOf(userId),
                            "filename", filename == null ? "" : filename
                    ))
                    .build()).content();
            String cleaned = CareerJsonResponseCleaner.cleanJsonResponse(content);
            String normalized = ResumeDateNormalizer.normalizeYearMonthDates(cleaned);
            JSONObject json = AgentResponseParser.jsonObject(normalized).orElse(null);
            if (json == null || json.isEmpty()) {
                return null;
            }
            CvBO parsed = JSON.parseObject(json.toJSONString(), CvBO.class);
            if (parsed == null) {
                return null;
            }
            return parsed.toBuilder()
                    .userId(userId)
                    .cvType(StringUtils.hasText(parsed.getCvType()) ? parsed.getCvType() : "upload")
                    .name(StringUtils.hasText(parsed.getName()) ? parsed.getName() : filename)
                    .summary(StringUtils.hasText(parsed.getSummary()) ? parsed.getSummary() : abbreviate(resumeText, 2000))
                    .build();
        } catch (Exception ex) {
            log.warn("AI 简历结构化失败，已回退到规则解析器。文件名={}", filename, ex);
            return null;
        }
    }

    private String systemPrompt() {
        return "You are a strict JSON decoder for resumes. Return exactly one valid JSON object and nothing else: no Markdown, no code fences, no commentary. "
                + "Use standard JSON quoting, escape embedded quotation marks, and never leave trailing commas. "
                + "Match CvBO fields only: name, title, contact, educations, experiences, projects, skills, certificates, summary, highlights, cvType. "
                + "Use [] for missing lists, {} for missing contact, null for unknown scalar values, and yyyy-MM-dd only for explicit dates. "
                + "Preserve resume facts; never invent information.";
    }

    private String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
