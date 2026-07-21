package com.hewei.hzyjy.xunzhi.interview.flow.extraction;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.hewei.hzyjy.xunzhi.agent.application.BusinessAgentResolver;
import com.hewei.hzyjy.xunzhi.agent.application.BusinessAgentScene;
import com.hewei.hzyjy.xunzhi.agent.dao.entity.AgentPropertiesDO;
import com.hewei.hzyjy.xunzhi.interview.api.io.req.InterviewQuestionReqDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.InterviewQuestionRespDTO;
import com.hewei.hzyjy.xunzhi.interview.application.guard.core.InterviewAiGuardException;
import com.hewei.hzyjy.xunzhi.interview.application.guard.core.InterviewAiGuardStage;
import com.hewei.hzyjy.xunzhi.interview.application.guard.lock.InterviewAiSessionLockService;
import com.hewei.hzyjy.xunzhi.interview.shared.InterviewAiInvoker;
import com.hewei.hzyjy.xunzhi.interview.shared.InterviewResponseParser;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionCacheService;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionService;
import com.hewei.hzyjy.xunzhi.toolkit.xunfei.XingChenAIClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewQuestionExtractionService {

    private static final String EXTRACTION_PROMPT =
            "Extract technical interview questions from the uploaded resume. "
                    + "Return JSON only with keys questions, sugest, type, and resumeScore. "
                    + "Do not output smallTalk, greetings, or fallback chat content.";

    private final BusinessAgentResolver businessAgentResolver;
    private final XingChenAIClient xingChenAIClient;
    private final InterviewAiInvoker interviewAiInvoker;
    private final InterviewAiSessionLockService interviewAiSessionLockService;
    private final InterviewQuestionService interviewQuestionService;
    private final InterviewQuestionCacheService interviewQuestionCacheService;
    private final InterviewResponseParser interviewResponseParser;

    public InterviewQuestionRespDTO extractInterviewQuestions(InterviewQuestionReqDTO reqDTO) {
        InterviewQuestionRespDTO response = new InterviewQuestionRespDTO();
        response.setSessionId(reqDTO.getSessionId());
        response.setUserName(reqDTO.getUserName());

        AgentPropertiesDO agentProperties = businessAgentResolver.resolveRequired(
                BusinessAgentScene.INTERVIEW_QUESTION_EXTRACTION);
        reqDTO.setAgentId(agentProperties.getId());
        response.setIsSuccess(0);

        // 哈希计算是纯本地操作，在获取分布式锁之前完成，减少锁占用时间。
        String resumeContentHash = computeResumeHash(reqDTO.getResumePdf(), reqDTO.getSessionId());

        RLock heavyLock = null;
        long startTime = System.currentTimeMillis();
        try {
            // 同一 session 的提取属于重操作，先拿会话级重锁，避免并发上传/提取造成重复消耗和状态覆盖。
            heavyLock = interviewAiSessionLockService.acquire(reqDTO.getSessionId(), InterviewAiGuardStage.INTERVIEW_EXTRACTION);
            if (heavyLock == null) {
            response.setErrorMessage("AI_OVERLOADED：题目解析正在处理中，请稍后重试");
                return response;
            }

            String fileUrl = uploadResumeIfPresent(reqDTO, agentProperties, response);
            if (fileUrl == null) {
                return response;
            }

            String fullContent = interviewAiInvoker.callAiSyncWithFile(
                    EXTRACTION_PROMPT,
                    reqDTO.getSessionId(),
                    agentProperties,
                    fileUrl,
                    InterviewAiGuardStage.INTERVIEW_EXTRACTION,
                    interviewAiInvoker.buildSingleFlightKey(InterviewAiGuardStage.INTERVIEW_EXTRACTION, reqDTO.getSessionId(), resumeContentHash)
            );

            long responseTime = System.currentTimeMillis() - startTime;
            reqDTO.setResumeFileUrl(fileUrl);

            // 先持久化原始响应，再做结构化解析；解析失败时仍可通过原始响应排障与回补。
            persistRawResponse(reqDTO, fullContent, responseTime);

            response.setResumeFileUrl(fileUrl);
            response.setResponseTime((int) responseTime);

            if (!populateStructuredResponse(reqDTO, response, fullContent)) {
                return response;
            }

            response.setIsSuccess(1);
        log.info("面试题目解析完成，sessionId={}", reqDTO.getSessionId());
            return response;
        } catch (InterviewAiGuardException e) {
            long responseTime = System.currentTimeMillis() - startTime;
        log.warn("面试题目解析被调用保护拦截，sessionId={}，错误码={}，错误信息={}",
                    reqDTO.getSessionId(), e.getErrorCode(), e.getMessage());
            try {
                // 失败也落库，但仅记录错误信息；结构化字段覆盖保护在 service 层统一处理。
                interviewQuestionService.createFromAIResponse(
                        reqDTO,
                        "{\"error\":\"" + e.getMessage() + "\"}",
                        (int) responseTime,
                        null
                );
            } catch (Exception saveException) {
        log.error("保存题目解析调用保护错误记录失败：{}", saveException.getMessage());
            }
            response.setErrorMessage(e.getMessage());
            response.setIsSuccess(0);
            return response;
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
        log.error("面试题目解析失败：{}", e.getMessage(), e);
            try {
                interviewQuestionService.createFromAIResponse(
                        reqDTO,
                        "{\"error\":\"" + e.getMessage() + "\"}",
                        (int) responseTime,
                        null
                );
            } catch (Exception saveException) {
        log.error("保存题目解析失败记录失败：{}", saveException.getMessage());
            }

        response.setErrorMessage("面试题目解析失败：" + e.getMessage());
            response.setIsSuccess(0);
            return response;
        } finally {
            interviewAiSessionLockService.release(heavyLock);
        }
    }

    private String uploadResumeIfPresent(
            InterviewQuestionReqDTO reqDTO,
            AgentPropertiesDO agentProperties,
            InterviewQuestionRespDTO response) {
        if (reqDTO.getResumePdf() == null || reqDTO.getResumePdf().isEmpty()) {
        response.setErrorMessage("简历文件不存在");
            return null;
        }
        try {
            String fileUrl = xingChenAIClient.uploadFile(
                    reqDTO.getResumePdf(),
                    agentProperties.getApiKey(),
                    agentProperties.getApiSecret()
            );
        log.info("简历文件上传成功，地址={}", fileUrl);
            return fileUrl;
        } catch (Exception e) {
        log.error("简历文件上传失败：{}", e.getMessage());
        response.setErrorMessage("简历文件上传失败");
            return null;
        }
    }

    private void persistRawResponse(InterviewQuestionReqDTO reqDTO, String fullContent, long responseTime) {
        try {
            interviewQuestionService.createFromAIResponse(
                    reqDTO,
                    fullContent,
                    (int) responseTime,
                    null
            );
        log.info("面试题目工作流响应已保存，sessionId={}", reqDTO.getSessionId());
        } catch (Exception e) {
        log.error("保存面试题目工作流响应失败，sessionId={}，错误信息={}",
                    reqDTO.getSessionId(), e.getMessage());
        }
    }

    private boolean populateStructuredResponse(
            InterviewQuestionReqDTO reqDTO,
            InterviewQuestionRespDTO response,
            String fullContent) {
        try {
        log.info("开始解析面试题目工作流响应，sessionId={}，响应长度={}，响应摘要={}",
                    reqDTO.getSessionId(),
                    fullContent == null ? 0 : fullContent.length(),
                    digestForLog(fullContent));

            String workflowErrorMessage = interviewResponseParser.extractWorkflowErrorMessage(fullContent);
            if (StrUtil.isNotBlank(workflowErrorMessage)) {
                response.setErrorMessage(workflowErrorMessage);
        log.warn("面试题目工作流返回错误，sessionId={}，错误信息={}",
                        reqDTO.getSessionId(), workflowErrorMessage);
                return false;
            }

            String extractedContent = interviewResponseParser.extractContentFromInterviewResponse(fullContent);
        log.info("已提取面试题目内容摘要，sessionId={}，内容长度={}，内容摘要={}",
                    reqDTO.getSessionId(),
                    extractedContent == null ? 0 : extractedContent.length(),
                    digestForLog(extractedContent));
            if (StrUtil.isBlank(extractedContent)) {
        response.setErrorMessage("面试题目工作流返回内容为空");
                return false;
            }

            Map<String, Object> responseMap = interviewResponseParser.extractStructuredResult(
                    extractedContent,
                    "questions",
                    "sugest",
                    "suggestions",
                    "resumeScore",
                    "type",
                    "smallTalk"
            );
            if (responseMap == null || responseMap.isEmpty()) {
        response.setErrorMessage("面试题目工作流响应解析失败");
        log.warn("面试题目工作流响应解析失败，结构化结果为空");
                return false;
            }

        log.info("面试题目工作流响应字段：{}", responseMap.keySet());
            Map<String, Object> resumeContext = buildResumeContext(responseMap);
            if (!resumeContext.isEmpty()) {
                interviewQuestionCacheService.cacheResumeContext(reqDTO.getSessionId(), resumeContext);
            }

            List<String> questions = normalizeStringList(responseMap.get("questions"));
            if (questions.isEmpty()) {
                String smallTalk = interviewResponseParser.asString(responseMap.get("smallTalk"));
                questions = fallbackQuestions();
        log.warn("面试题目解析未返回可用题目，已使用本地兜底题目，sessionId={}，闲聊内容={}",
                        reqDTO.getSessionId(), smallTalk);
            }

            interviewQuestionCacheService.cacheInterviewQuestions(reqDTO.getSessionId(), questions);
            Map<String, String> questionMap =
                    interviewQuestionCacheService.getSessionInterviewQuestions(reqDTO.getSessionId());
            response.setQuestions(questionMap);
            response.setQuestionCount(questions.size());
            interviewQuestionCacheService.initInterviewFlow(reqDTO.getSessionId(), questions.size());

            List<String> suggestions = normalizeSuggestions(responseMap);
            if (!suggestions.isEmpty()) {
                interviewQuestionCacheService.cacheInterviewSuggestions(reqDTO.getSessionId(), suggestions);
                Map<String, String> suggestionMap =
                        interviewQuestionCacheService.getSessionInterviewSuggestions(reqDTO.getSessionId());
                response.setSuggestions(suggestionMap);
                response.setSuggestionCount(suggestions.size());
            } else {
        log.warn("面试题目工作流响应未包含建议字段");
            }

            // type 字段兼容历史别名，保证 interviewDirection/interviewType 在不同模型输出下都能回补。
            String interviewType = interviewResponseParser.asString(responseMap.get("type"));
            if (StrUtil.isBlank(interviewType)) {
                interviewType = interviewResponseParser.asString(responseMap.get("interviewType"));
            }
            if (StrUtil.isBlank(interviewType)) {
                interviewType = interviewResponseParser.asString(responseMap.get("direction"));
            }
            if (StrUtil.isBlank(interviewType)) {
                interviewType = interviewResponseParser.asString(responseMap.get("interviewDirection"));
            }
            if (StrUtil.isNotBlank(interviewType)) {
                interviewQuestionCacheService.cacheInterviewDirection(reqDTO.getSessionId(), interviewType);
                response.setInterviewType(interviewType);
            } else {
        log.warn("面试题目工作流响应未包含面试类型字段");
            }

            Integer resumeScore = interviewResponseParser.parseScoreFromResponse(responseMap, "resumeScore");
            if (resumeScore != null) {
                interviewQuestionCacheService.cacheResumeScore(reqDTO.getSessionId(), resumeScore);
                response.setResumeScore(resumeScore);
            } else {
        log.warn("面试题目工作流响应未包含有效的简历评分字段");
            }

            // 结构化二次落库用于 Redis 丢失后的恢复来源，避免报告阶段出现字段缺失。
            persistStructuredFields(reqDTO, questions, suggestions, resumeScore, interviewType, resumeContext);
            interviewQuestionCacheService.resetSessionScore(reqDTO.getSessionId());
        log.info("面试会话总分已重置，sessionId={}", reqDTO.getSessionId());
            return true;
        } catch (Exception cacheException) {
        response.setErrorMessage("面试题目工作流响应解析失败");
            log.error(
                "缓存面试题目工作流响应失败，sessionId={}，错误信息={}",
                    reqDTO.getSessionId(),
                    cacheException.getMessage()
            );
            return false;
        }
    }

    private List<String> normalizeSuggestions(Map<String, Object> responseMap) {
        if (responseMap == null || responseMap.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> suggestions = normalizeStringList(responseMap.get("sugest"));
        if (!suggestions.isEmpty()) {
            return suggestions;
        }
        return normalizeStringList(responseMap.get("suggestions"));
    }

    private List<String> normalizeStringList(Object value) {
        return interviewResponseParser.asStringList(value);
    }

    private List<String> fallbackQuestions() {
        return List.of(
                "请结合简历介绍一个你最有代表性的项目，并说明你的具体职责。",
                "在项目推进过程中，你遇到过什么困难？你是如何分析和解决的？",
                "如果让你重新设计这个项目，你会优先改进哪一部分，为什么？"
        );
    }

    /**
     * 计算简历文件内容的 SHA-256 哈希，用于 single-flight 去重。
     * 相同文件内容产生相同哈希，避免因上传 URL 每次变化导致去重失效。
     *
     * @param resumePdf 简历文件
     * @param sessionId 会话标识，仅用于日志
     * @return 文件内容的 SHA-256 十六进制字符串，文件为空时返回 null
     */
    private String computeResumeHash(MultipartFile resumePdf, String sessionId) {
        if (resumePdf == null || resumePdf.isEmpty()) {
        log.debug("简历 PDF 为空，无法计算内容摘要，sessionId={}", sessionId);
            return null;
        }
        try {
            byte[] fileBytes = resumePdf.getBytes();
            String hash = DigestUtil.sha256Hex(fileBytes);
        log.debug("已计算简历内容摘要，sessionId={}，摘要={}", sessionId, hash);
            return hash;
        } catch (Exception e) {
        log.warn("读取简历 PDF 字节并计算内容摘要失败，sessionId={}，错误信息={}",
                    sessionId, e.getMessage());
            return null;
        }
    }

    private String digestForLog(String value) {
        if (StrUtil.isBlank(value)) {
            return "-";
        }
        return DigestUtil.sha256Hex(value).substring(0, 16);
    }

    private void persistStructuredFields(
            InterviewQuestionReqDTO reqDTO,
            List<String> questions,
            List<String> suggestions,
            Integer resumeScore,
            String interviewType,
            Map<String, Object> resumeContext) {
        try {
            interviewQuestionService.upsertStructuredExtraction(
                    reqDTO.getSessionId(),
                    reqDTO.getUserName(),
                    reqDTO.getAgentId(),
                    reqDTO.getResumeFileUrl(),
                    questions,
                    suggestions,
                    resumeScore,
                    interviewType,
                    resumeContext
            );
        } catch (Exception ex) {
        log.warn("持久化结构化题目解析字段失败，sessionId={}，错误信息={}",
                    reqDTO.getSessionId(), ex.getMessage(), ex);
        }
    }

    private Map<String, Object> buildResumeContext(Map<String, Object> responseMap) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (responseMap == null || responseMap.isEmpty()) {
            return context;
        }
        for (Map.Entry<String, Object> entry : responseMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if ("questions".equals(key) || "sugest".equals(key) || "suggestions".equals(key)) {
                continue;
            }
            context.put(key, value);
        }
        return context;
    }
}
