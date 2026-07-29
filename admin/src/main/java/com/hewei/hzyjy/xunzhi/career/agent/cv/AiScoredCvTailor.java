package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hewei.hzyjy.xunzhi.career.agent.support.AgentResponseParser;
import com.hewei.hzyjy.xunzhi.career.ai.AgentRuntimeGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.security.JobDescriptionSafetyContext;
import com.hewei.hzyjy.xunzhi.career.security.JobDescriptionSafetyService;
import com.hewei.hzyjy.xunzhi.career.skill.CareerSkillRegistry;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
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
    private final JobDescriptionSafetyService jobDescriptionSafetyService;

    @Autowired
    public AiScoredCvTailor(
            AiGateway aiGateway,
            ObjectProvider<AgentRuntimeGateway> agentRuntimeGatewayProvider,
            ObjectProvider<CareerSkillRegistry> skillRegistryProvider) {
        this(aiGateway, agentRuntimeGatewayProvider, skillRegistryProvider, new JobDescriptionSafetyService());
    }

    /** 兼容直接构造与旧测试；Spring 容器统一使用下方四参数构造器。 */
    public AiScoredCvTailor(
            AiGateway aiGateway,
            ObjectProvider<AgentRuntimeGateway> agentRuntimeGatewayProvider,
            ObjectProvider<CareerSkillRegistry> skillRegistryProvider,
            ObjectProvider<JobDescriptionSafetyService> jobDescriptionSafetyServiceProvider) {
        this(aiGateway, agentRuntimeGatewayProvider, skillRegistryProvider,
                jobDescriptionSafetyServiceProvider.getIfAvailable(JobDescriptionSafetyService::new));
    }

    private AiScoredCvTailor(
            AiGateway aiGateway,
            ObjectProvider<AgentRuntimeGateway> agentRuntimeGatewayProvider,
            ObjectProvider<CareerSkillRegistry> skillRegistryProvider,
            JobDescriptionSafetyService jobDescriptionSafetyService) {
        this.aiGateway = aiGateway;
        this.agentRuntimeGatewayProvider = agentRuntimeGatewayProvider;
        this.skillRegistry = skillRegistryProvider.getIfAvailable(CareerSkillRegistry::disabled);
        this.jobDescriptionSafetyService = jobDescriptionSafetyService == null
                ? new JobDescriptionSafetyService() : jobDescriptionSafetyService;
    }

    public AiScoredCvTailor(AiGateway aiGateway) {
        this(aiGateway, CareerSkillRegistry.disabled());
    }

    public AiScoredCvTailor(AiGateway aiGateway, CareerSkillRegistry skillRegistry) {
        this.aiGateway = aiGateway;
        this.agentRuntimeGatewayProvider = null;
        this.skillRegistry = skillRegistry == null ? CareerSkillRegistry.disabled() : skillRegistry;
        this.jobDescriptionSafetyService = new JobDescriptionSafetyService();
    }

    @Override
    public CvBO tailor(CvBO cv, CvReview review, List<String> referenceTemplates) {
        return tailor(cv, "", review, referenceTemplates);
    }

    @Override
    public CvBO tailor(CvBO cv, String jobDescription, CvReview review, List<String> referenceTemplates) {
        String safeJobDescription = safeJobProfile(jobDescription);
        CvBO gatewayCv = tryLangChain4jCv(cv, safeJobDescription, review, referenceTemplates);
        if (gatewayCv != null) {
            return finalizeTailoredCv(cv, review, gatewayCv, null);
        }
        String response = tryLangChain4jText(cv, safeJobDescription, review, referenceTemplates);
        if (response == null || response.isBlank()) {
            response = aiGateway.chat(AiPromptRequest.builder()
                    .sceneCode("RESUME_TAILOR")
                    .systemPrompt(buildSystemPrompt(cv))
                    .userPrompt(CvPromptTemplates.tailorUserPrompt(cv, safeJobDescription, review, referenceTemplates))
                    .build()).content();
        }
        CvBO parsedCv = parseStructuredCv(response);
        if (parsedCv != null) {
            return finalizeTailoredCv(cv, review, parsedCv, response);
        }
        JSONObject json = AgentResponseParser.jsonObject(response).orElse(null);
        String title = json == null ? null : json.getString("title");
        String summary = json == null ? null : json.getString("summary");
        String advice = json == null ? response : firstNonBlank(json.getString("advice"), json.getString("feedback"), response);
        String mergedAdvice = mergeAdvice(review, advice);
        return cv.toBuilder()
                .title(firstNonBlank(title, cv.getTitle()))
                .advice(mergedAdvice)
                .summary(firstNonBlank(summary, mergeSummary(cv.getSummary(), review.feedback())))
                .build();
    }

    private CvBO tryLangChain4jCv(CvBO cv, String jobDescription, CvReview review, List<String> referenceTemplates) {
        AgentRuntimeGateway gateway = agentRuntimeGatewayProvider == null ? null : agentRuntimeGatewayProvider.getIfAvailable();
        if (gateway == null) {
            return null;
        }
        try {
            return gateway.invoke("ScoredCvTailor", "tailor", Map.of(
                    "cv", cv,
                    "jobDescription", jobDescription,
                    "cvReview", review,
                    "referenceTemplates", referenceTemplates == null ? List.of() : referenceTemplates
            ), CvBO.class);
        } catch (Exception ex) {
            log.debug("LangChain4j ScoredCvTailor structured CvBO unavailable, falling back to text response", ex);
            return null;
        }
    }

    private String tryLangChain4jText(CvBO cv, String jobDescription, CvReview review, List<String> referenceTemplates) {
        AgentRuntimeGateway gateway = agentRuntimeGatewayProvider == null ? null : agentRuntimeGatewayProvider.getIfAvailable();
        if (gateway == null) {
            return null;
        }
        try {
            return gateway.invoke("ScoredCvTailor", "tailor", Map.of(
                    "cv", cv,
                    "jobDescription", jobDescription,
                    "cvReview", review,
                    "referenceTemplates", referenceTemplates == null ? List.of() : referenceTemplates
            ), String.class);
        } catch (Exception ex) {
            log.debug("LangChain4j ScoredCvTailor unavailable, falling back to Spring AI", ex);
            return null;
        }
    }

    private CvBO parseStructuredCv(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        String candidate = AgentResponseParser.jsonObject(response)
                .map(JSONObject::toString)
                .orElse(null);
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(candidate, CvBO.class);
        } catch (Exception ex) {
            log.debug("Failed to parse tailored CvBO from response", ex);
            return null;
        }
    }

    private CvBO finalizeTailoredCv(CvBO original, CvReview review, CvBO tailored, String rawResponse) {
        if (tailored == null) {
            return original;
        }
        String adviceFromTailor = firstNonBlank(
                tailored.getAdvice(),
                AgentResponseParser.feedback(rawResponse).orElse(null),
                rawResponse
        );
        return tailored.toBuilder()
                .id(original == null ? null : original.getId())
                .userId(original == null ? null : original.getUserId())
                .cvType(firstNonBlank(tailored.getCvType(), original == null ? null : original.getCvType()))
                .name(firstNonBlank(tailored.getName(), original == null ? null : original.getName()))
                .birthDate(tailored.getBirthDate() != null ? tailored.getBirthDate() : original == null ? null : original.getBirthDate())
                .avatarUrl(firstNonBlank(tailored.getAvatarUrl(), original == null ? null : original.getAvatarUrl()))
                .advice(mergeAdvice(review, adviceFromTailor))
                .summary(firstNonBlank(tailored.getSummary(), mergeSummary(original == null ? null : original.getSummary(), review.feedback())))
                .optimizationHistory(original == null ? tailored.getOptimizationHistory() : original.getOptimizationHistory())
                .build();
    }

    private String mergeAdvice(CvReview review, String tailorAdvice) {
        String reviewFeedback = review == null ? null : review.feedback();
        String safeTailorAdvice = firstNonBlank(tailorAdvice, reviewFeedback, "");
        if (reviewFeedback == null || reviewFeedback.isBlank()) {
            return safeTailorAdvice;
        }
        if (safeTailorAdvice == null || safeTailorAdvice.isBlank() || reviewFeedback.equals(safeTailorAdvice)) {
            return reviewFeedback;
        }
        return reviewFeedback + "\n\nTailor advice:\n" + safeTailorAdvice;
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

    /**
     * 独立使用改写器时仍执行一次安全检查，避免外部调用方绕开优化编排器直接传入原始 JD。
     * 空岗位资料保留旧三参数接口的兼容行为，但不会把任意原始文本带入 Prompt。
     */
    private String safeJobProfile(String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) {
            return "";
        }
        JobDescriptionSafetyContext safety = jobDescriptionSafetyService.assess(jobDescription);
        if (safety.rejected()) {
            throw new ClientException("岗位描述包含与招聘无关的指令性内容，且未识别到有效岗位要求，请修正后重试");
        }
        return safety.safeOptimizationContext();
    }
}
