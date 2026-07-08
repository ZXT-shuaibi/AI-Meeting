package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hewei.hzyjy.xunzhi.career.agent.support.AgentResponseParser;
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
            JSONObject json = AgentResponseParser.jsonObject(content).orElse(null);
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
            log.warn("AI resume structuring failed, falling back to heuristic parser. filename={}", filename, ex);
            return null;
        }
    }

    private String systemPrompt() {
        return "Extract the resume into strict JSON matching CvBO fields. "
                + "Return only JSON. Preserve contact, educations, experiences, projects, skills, certificates, summary, title, and highlights when present. "
                + "Use arrays for list fields and ISO yyyy-MM-dd for dates when dates are explicit.";
    }

    private String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}