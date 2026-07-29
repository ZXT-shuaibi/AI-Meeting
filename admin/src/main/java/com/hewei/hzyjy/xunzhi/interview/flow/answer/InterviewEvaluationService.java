package com.hewei.hzyjy.xunzhi.interview.flow.answer;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.hewei.hzyjy.xunzhi.agent.dao.entity.AgentPropertiesDO;
import com.hewei.hzyjy.xunzhi.interview.application.history.InterviewHistoryContext;
import com.hewei.hzyjy.xunzhi.interview.application.history.InterviewHistoryContextProvider;
import com.hewei.hzyjy.xunzhi.interview.application.guard.core.InterviewAiGuardException;
import com.hewei.hzyjy.xunzhi.interview.application.guard.core.InterviewAiGuardStage;
import com.hewei.hzyjy.xunzhi.interview.shared.InterviewAiInvoker;
import com.hewei.hzyjy.xunzhi.interview.shared.InterviewResponseParser;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionCacheService;
import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewEvaluationService {

    private static final String KEY_AGENT_USER_INPUT = "AGENT_USER_INPUT";
    private static final String KEY_QUESTION = "question";
    private static final String KEY_RESUME_CONTEXT = "resume_context";
    private static final String KEY_INTERVIEW_HISTORY_CONTEXT = "interview_history_context";
    private static final String KEY_SCORE = "score";
    private static final String KEY_LOGIC_OK = "logic_ok";
    private static final String KEY_MISSING_POINTS = "missing_points";
    private static final String KEY_FEEDBACK = "feedback";
    private static final String KEY_FOLLOW_UP_NEEDED = "follow_up_needed";
    private static final String KEY_FOLLOW_UP_QUESTION = "follow_up_question";

    private static final String[] RESUME_SUMMARY_KEYS = new String[]{
            "resume_context",
            "resume_summary",
            "resumeSummary",
            "candidate_summary",
            "candidateSummary",
            "profile_summary",
            "profileSummary",
            "summary"
    };

    private final InterviewQuestionCacheService interviewQuestionCacheService;
    private final InterviewAiInvoker interviewAiInvoker;
    private final InterviewResponseParser interviewResponseParser;
    private final InterviewHistoryContextProvider interviewHistoryContextProvider;

    /**
     * 调用评分工作流评估当前回答，并统一归一化为后续流程可消费的评分字段。
     *
     * <p>远程工作流异常、空响应或字段不完整时，本方法不会伪造远程结果；而是返回带有明确
     * 降级标记的本地可用结果，保证追问和面试状态机能继续受控运行。</p>
     */
    public Map<String, Object> evaluateAnswer(
            String sessionId,
            String requestId,
            String questionNumber,
            String questionContent,
            String answerContent,
            AgentPropertiesDO scorerAgent) {

        // 1) 先走评分工作流（带参数化上下文），拿标准结构化评分。
        InterviewHistoryContext historyContext = interviewHistoryContextProvider.load(sessionId);
        Map<String, Object> evaluationResult = evaluateAnswerByScorerAgent(
                sessionId, requestId, questionNumber, questionContent, answerContent, scorerAgent, historyContext, false);
        // 评分器是远程工作流；修复重试必须沿用完整变量协议，不能只传自由文本，
        // 否则工作流中的 question 输入会为空，导致远程工作流无法完成评分。
        if (requiresStrictFallback(evaluationResult)) {
            if (isMissingInputPlaceholder(evaluationResult)) {
                Metrics.counter("interview_evaluation_placeholder_rejected_total").increment();
                log.warn(
                        "已拒绝评分工作流返回的“缺少输入”示例占位结果，sessionId={}，requestId={}，题号={}",
                        sessionId,
                        requestId,
                        questionNumber
                );
            }
            evaluationResult = evaluateAnswerByScorerAgent(
                    sessionId, requestId, questionNumber, questionContent, answerContent, scorerAgent, historyContext, true);
        }
        if (evaluationResult == null) {
            return null;
        }

        // 3) 最后统一字段归一化，补全 followUp/feedback 等默认值。
        Map<String, Object> normalized = normalizeScorerResult(evaluationResult);
        if (isMissingInputPlaceholder(normalized)) {
            Metrics.counter("interview_evaluation_placeholder_rejected_total").increment();
            log.warn(
                    "已拒绝评分修复重试返回的“缺少输入”示例占位结果，sessionId={}，requestId={}，题号={}",
                    sessionId,
                    requestId,
                    questionNumber
            );
            clearMissingInputPlaceholder(normalized);
        }
        inferFollowUpNeededIfMissing(normalized);
        ensureDefaultEvaluationFields(normalized);
        ensureScoreWithLocalFallback(normalized, sessionId, requestId, questionNumber, answerContent);
        return normalized;
    }

    private Map<String, Object> evaluateAnswerByScorerAgent(
            String sessionId,
            String requestId,
            String questionNumber,
            String questionContent,
            String answerContent,
            AgentPropertiesDO scorerAgent,
            InterviewHistoryContext historyContext,
            boolean repairAttempt) {
        try {
            String resumeContextText = buildResumeContextText(
                    interviewQuestionCacheService.getSessionResumeContext(sessionId));

            Map<String, Object> parameters = buildScorerWorkflowParameters(
                    answerContent,
                    questionContent,
                    resumeContextText,
                    historyContext.toPromptText(1400));

            logWorkflowParameters(
                    repairAttempt ? "scorer-repair" : "scorer",
                    sessionId,
                    requestId,
                    questionNumber,
                    scorerAgent,
                    parameters
            );

            String workflowResponse = interviewAiInvoker.callAiSyncWithParameters(
                    sessionId + "_score",
                    scorerAgent,
                    parameters,
                    InterviewAiGuardStage.INTERVIEW_EVALUATION,
                    interviewAiInvoker.buildSingleFlightKey(
                            InterviewAiGuardStage.INTERVIEW_EVALUATION,
                            sessionId,
                            questionNumber,
                            answerContent
                    ) + (repairAttempt ? "|repair" : "")
            );
            Map<String, Object> parsed = interviewResponseParser.parseEvaluationResult(workflowResponse);
            Map<String, Object> normalized = normalizeScorerResult(parsed);
            logInvalidScorerResponseShape(
                    repairAttempt ? "scorer-repair" : "scorer",
                    sessionId,
                    requestId,
                    questionNumber,
                    workflowResponse,
                    parsed,
                    normalized
            );
            return normalized;
        } catch (InterviewAiGuardException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("评分工作流调用失败，sessionId={}", sessionId, ex);
            return null;
        }
    }

    /**
     * 当远程评分工作流违反输出契约时，仅记录响应结构，便于定位工作流字段映射问题。
     * 故意不记录字段值，避免候选人回答和模型反馈进入服务端日志。
     */
    private void logInvalidScorerResponseShape(
            String stage,
            String sessionId,
            String requestId,
            String questionNumber,
            String rawResponse,
            Map<String, Object> parsed,
            Map<String, Object> normalized) {
        if (hasValidScore(normalized)) {
            return;
        }
        String workflowError = interviewResponseParser.extractWorkflowErrorMessage(rawResponse);
        Object rawScore = normalized == null ? null : normalized.get(KEY_SCORE);
        log.warn(
                "远程评分工作流未返回有效分值，阶段={}，sessionId={}，requestId={}，题号={}，原始响应长度={}，解析字段={}，归一化字段={}，分值类型={}，工作流错误={}",
                stage,
                sessionId,
                requestId,
                questionNumber,
                rawResponse == null ? 0 : rawResponse.length(),
                fieldNames(parsed),
                fieldNames(normalized),
                rawScore == null ? "absent" : rawScore.getClass().getSimpleName(),
                workflowError
        );
    }

    private String fieldNames(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return "[]";
        }
        return result.keySet().toString();
    }

    private Map<String, Object> buildScorerWorkflowParameters(
            String answerContent,
            String questionContent,
            String resumeContextText,
            String historyContextText) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put(KEY_AGENT_USER_INPUT, answerContent);
        parameters.put(KEY_QUESTION, questionContent);
        parameters.put(KEY_RESUME_CONTEXT, resumeContextText);
        parameters.put(KEY_INTERVIEW_HISTORY_CONTEXT, historyContextText);
        return parameters;
    }

    private void logWorkflowParameters(
            String stage,
            String sessionId,
            String requestId,
            String questionNumber,
            AgentPropertiesDO agent,
            Map<String, Object> parameters) {
        Map<String, Object> debugView = new LinkedHashMap<>();
        if (parameters != null) {
            parameters.forEach((key, value) -> {
                if (value == null) {
                    return;
                }
                if (KEY_AGENT_USER_INPUT.equals(key)
                        || KEY_RESUME_CONTEXT.equals(key)
                        || KEY_INTERVIEW_HISTORY_CONTEXT.equals(key)) {
                    debugView.put(key, clip(String.valueOf(value), 300));
                    return;
                }
                debugView.put(key, value);
            });
        }

        log.info(
                "评分工作流请求，阶段={}，sessionId={}，requestId={}，题号={}，流程ID={}，参数={}",
                stage,
                sessionId,
                requestId,
                questionNumber,
                agent == null ? null : agent.getApiFlowId(),
                JSON.toJSONString(debugView)
        );
    }

    private void inferFollowUpNeededIfMissing(Map<String, Object> result) {
        if (result == null || result.containsKey(KEY_FOLLOW_UP_NEEDED)) {
            return;
        }
        boolean logicOk = interviewResponseParser.asBoolean(result.get(KEY_LOGIC_OK));
        List<String> missingPoints = interviewResponseParser.asStringList(result.get(KEY_MISSING_POINTS));
        String followUpQuestion = interviewResponseParser.asString(result.get(KEY_FOLLOW_UP_QUESTION));
        boolean inferredFollowUp = !logicOk
                || (missingPoints != null && !missingPoints.isEmpty())
                || StrUtil.isNotBlank(followUpQuestion);
        result.put(KEY_FOLLOW_UP_NEEDED, inferredFollowUp);
    }

    private void ensureDefaultEvaluationFields(Map<String, Object> result) {
        if (result == null) {
            return;
        }
        if (!result.containsKey(KEY_MISSING_POINTS)) {
            result.put(KEY_MISSING_POINTS, Collections.emptyList());
        }
        if (!result.containsKey(KEY_FEEDBACK)) {
            result.put(KEY_FEEDBACK, "");
        }
        if (!result.containsKey(KEY_FOLLOW_UP_QUESTION)) {
            result.put(KEY_FOLLOW_UP_QUESTION, "");
        }
    }

    private void ensureScoreWithLocalFallback(
            Map<String, Object> result,
            String sessionId,
            String requestId,
            String questionNumber,
            String answerContent) {
        if (result == null || hasValidScore(result)) {
            return;
        }

        int localScore = calculateConservativeLocalScore(answerContent);
        result.put(KEY_SCORE, localScore);
        String feedback = interviewResponseParser.asString(result.get(KEY_FEEDBACK));
        String fallbackNotice = "\u672c\u8f6e\u8bc4\u5206\u6a21\u578b\u672a\u8fd4\u56de\u6709\u6548\u5206\u503c\uff0c\u5df2\u4f7f\u7528\u4fdd\u5b88\u672c\u5730\u8bc4\u5206\uff0c\u8bf7\u7ed3\u5408\u53cd\u9988\u53c2\u8003\u3002";
        if (StrUtil.isBlank(feedback)) {
            result.put(KEY_FEEDBACK, fallbackNotice);
        } else {
            result.put(KEY_FEEDBACK, feedback + "\n" + fallbackNotice);
        }
        result.putIfAbsent(KEY_FOLLOW_UP_NEEDED, true);
        Metrics.counter("interview_evaluation_local_score_fallback_total").increment();
        log.warn(
                "评分工作流及完整协议修复重试均未返回有效分值，已使用保守本地评分，sessionId={}，requestId={}，题号={}，本地分值={}",
                sessionId,
                requestId,
                questionNumber,
                localScore
        );
    }

    private int calculateConservativeLocalScore(String answerContent) {
        String normalized = StrUtil.trimToEmpty(answerContent);
        if (normalized.isEmpty()) {
            return 0;
        }
        int length = normalized.length();
        if (length < 40) {
            return 30;
        }
        if (length < 120) {
            return 45;
        }
        if (length < 240) {
            return 60;
        }
        return 70;
    }

    private Map<String, Object> normalizeScorerResult(Map<String, Object> rawResult) {
        if (rawResult == null || rawResult.isEmpty()) {
            return rawResult;
        }

        Map<String, Object> normalized = new LinkedHashMap<>(rawResult);

        Integer score = extractScoreByKeys(
                normalized,
                KEY_SCORE,
                "total_score",
                "composite_score",
                "evaluation_score",
                "final_score",
                "score_value"
        );
        if (score != null) {
            normalized.put(KEY_SCORE, score);
        }

        Boolean logicOk = extractBooleanByKeys(normalized, KEY_LOGIC_OK, "logicOk");
        if (logicOk != null) {
            normalized.put(KEY_LOGIC_OK, logicOk);
        }

        List<String> missingPoints = extractStringListByKeys(normalized, KEY_MISSING_POINTS, "missingPoints", "lack_points");
        if (missingPoints != null) {
            normalized.put(KEY_MISSING_POINTS, missingPoints);
        }

        String feedback = extractStringByKeys(normalized, KEY_FEEDBACK, "comment", "suggestion");
        if (feedback != null) {
            normalized.put(KEY_FEEDBACK, feedback);
        }

        Boolean followUpNeeded = extractBooleanByKeys(normalized, KEY_FOLLOW_UP_NEEDED, "followUpNeeded");
        if (followUpNeeded != null) {
            normalized.put(KEY_FOLLOW_UP_NEEDED, followUpNeeded);
        }

        String followUpQuestion = extractStringByKeys(
                normalized, KEY_FOLLOW_UP_QUESTION, "followUpQuestion", "ask_to_user", "ask");
        if (followUpQuestion != null) {
            normalized.put(KEY_FOLLOW_UP_QUESTION, followUpQuestion);
        }

        return normalized;
    }

    private boolean hasValidScore(Map<String, Object> result) {
        return extractScoreByKeys(
                result,
                KEY_SCORE,
                "total_score",
                "composite_score",
                "evaluation_score",
                "final_score",
                "score_value"
        ) != null;
    }

    /**
     * 识别评分工作流误回传的“缺少输入”示例，避免其被当作真实评分结果。
     *
     * <p>该示例通常含有 {@code score=0}，若只校验分值会误判为有效评分，继而为候选人生成
     * 无意义的追问。因此需同时检查反馈内容和结构化字段。</p>
     */
    private boolean requiresStrictFallback(Map<String, Object> result) {
        return result == null
                || result.isEmpty()
                || !hasValidScore(result)
                || isMissingInputPlaceholder(result);
    }

    private boolean isMissingInputPlaceholder(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return false;
        }
        String feedback = interviewResponseParser.asString(result.get(KEY_FEEDBACK));
        String followUpQuestion = interviewResponseParser.asString(result.get(KEY_FOLLOW_UP_QUESTION));
        List<String> missingPoints = interviewResponseParser.asStringList(result.get(KEY_MISSING_POINTS));
        String combined = String.join(
                "\n",
                StrUtil.blankToDefault(feedback, ""),
                StrUtil.blankToDefault(followUpQuestion, ""),
                String.join("\n", missingPoints == null ? Collections.emptyList() : missingPoints)
        ).toLowerCase(Locale.ROOT);

        return combined.contains("缺少评分所需")
                || combined.contains("未提供需要评分")
                || combined.contains("请提供技术面试题目")
                || combined.contains("面试者答案文本")
                || combined.contains("missing question")
                || combined.contains("missing answer");
    }

    private void clearMissingInputPlaceholder(Map<String, Object> result) {
        if (result == null) {
            return;
        }
        result.remove(KEY_SCORE);
        result.remove("total_score");
        result.remove("composite_score");
        result.remove("evaluation_score");
        result.remove("final_score");
        result.remove("score_value");
        result.remove(KEY_FEEDBACK);
        result.remove(KEY_FOLLOW_UP_NEEDED);
        result.remove("followUpNeeded");
        result.remove(KEY_FOLLOW_UP_QUESTION);
        result.remove("followUpQuestion");
        result.put(KEY_MISSING_POINTS, Collections.emptyList());
    }

    private Integer extractScoreByKeys(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Integer score = interviewResponseParser.parseScoreFromResponse(source, key);
            if (score != null) {
                return score;
            }
        }
        return null;
    }

    private Boolean extractBooleanByKeys(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return interviewResponseParser.asBoolean(source.get(key));
            }
        }
        return null;
    }

    private List<String> extractStringListByKeys(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return interviewResponseParser.asStringList(source.get(key));
            }
        }
        return null;
    }

    private String extractStringByKeys(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                String value = interviewResponseParser.asString(source.get(key));
                return value == null ? "" : value;
            }
        }
        return null;
    }

    private String buildResumeContextText(Map<String, Object> resumeContext) {
        if (resumeContext == null || resumeContext.isEmpty()) {
            return "";
        }

        String preferredSummary = extractNonBlankStringByKeys(resumeContext, RESUME_SUMMARY_KEYS);
        if (StrUtil.isNotBlank(preferredSummary)) {
            return clip(preferredSummary, 2000);
        }

        Map<String, Object> filteredContext = new LinkedHashMap<>();
        resumeContext.forEach((key, value) -> {
            if (StrUtil.isBlank(key) || value == null) {
                return;
            }
            if ("questions".equals(key)
                    || "suggestions".equals(key)
                    || "sugest".equals(key)
                    || "resumeScore".equals(key)
                    || "score".equals(key)
                    || "type".equals(key)
                    || "interviewType".equals(key)) {
                return;
            }
            filteredContext.put(key, value);
        });

        Map<String, Object> contextToUse = filteredContext.isEmpty() ? resumeContext : filteredContext;
        return clip(JSON.toJSONString(contextToUse), 2000);
    }

    private String extractNonBlankStringByKeys(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (!source.containsKey(key)) {
                continue;
            }
            String value = interviewResponseParser.asString(source.get(key));
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String clip(String value, int maxLen) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        String cleaned = value.trim().replaceAll("\\s+", " ");
        if (cleaned.length() <= maxLen) {
            return cleaned;
        }
        return cleaned.substring(0, maxLen);
    }
}
