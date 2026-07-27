package com.hewei.hzyjy.xunzhi.interview.flow.report;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hewei.hzyjy.xunzhi.career.agent.support.AgentResponseParser;
import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import com.hewei.hzyjy.xunzhi.career.harness.application.AgentRunCoordinator;
import com.hewei.hzyjy.xunzhi.career.harness.model.AgentRunStartCommand;
import com.hewei.hzyjy.xunzhi.interview.application.history.InterviewHistoryContext;
import com.hewei.hzyjy.xunzhi.interview.application.history.InterviewHistoryContextProvider;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.InterviewReviewFeedbackRespDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.RadarChartDTO;
import com.hewei.hzyjy.xunzhi.interview.service.model.InterviewTurnLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewReportAiReviewer {

    private static final int MAX_ITEMS = 3;
    private static final int MAX_PROMPT_LENGTH = 14_000;

    private final AiGateway aiGateway;
    private InterviewHistoryContextProvider interviewHistoryContextProvider;
    private AgentRunCoordinator agentRunCoordinator;

    @Autowired
    void setInterviewHistoryContextProvider(InterviewHistoryContextProvider interviewHistoryContextProvider) {
        this.interviewHistoryContextProvider = interviewHistoryContextProvider;
    }

    /**
     * 运行审计是可选旁路能力。旧环境尚未执行 Harness 建表脚本时，面试报告仍会继续生成，
     * 不能因为审计表不可用而破坏既有 AI 报告与本地兜底逻辑。
     */
    @Autowired(required = false)
    void setAgentRunCoordinator(AgentRunCoordinator agentRunCoordinator) {
        this.agentRunCoordinator = agentRunCoordinator;
    }

    public InterviewReviewFeedbackRespDTO review(
            String sessionId,
            String interviewDirection,
            List<InterviewTurnLog> turns,
            RadarChartDTO radarChart,
            String interviewSuggestions) {
        return review(null, sessionId, interviewDirection, turns, radarChart, interviewSuggestions);
    }

    /**
     * 生成一次 AI 面试报告，并仅记录轮次数、阶段结果等脱敏运行摘要。
     * 模型返回无效内容时仍返回 null，由现有调用方生成本地兜底报告。
     */
    public InterviewReviewFeedbackRespDTO review(
            Long userId,
            String sessionId,
            String interviewDirection,
            List<InterviewTurnLog> turns,
            RadarChartDTO radarChart,
            String interviewSuggestions) {
        String runId = startHarnessRun(userId, sessionId, turns);
        try {
            InterviewHistoryContext historyContext = interviewHistoryContextProvider == null
                    ? InterviewHistoryContext.empty()
                    : interviewHistoryContextProvider.load(sessionId);
            stageHarnessRun(runId, "HISTORY_CONTEXT", "面试历史上下文已投影", Map.of(
                    "resultCount", historyContext.available() ? historyContext.recentTurns().size() : 0));
            stageHarnessRun(runId, "MODEL_CALL", "开始生成 AI 面试报告", Map.of());
            AiGatewayResult result = aiGateway.chat(AiPromptRequest.builder()
                    .sceneCode("interview-report-review")
                    .sessionId(sessionId)
                    .systemPrompt("你是一名资深中文面试教练。根据提供的面试事实生成个性化复盘。只输出合法 JSON，禁止 Markdown 和额外说明。格式固定为：{\"overallComment\":\"不超过120字的中文总体评价\",\"highlights\":[\"中文亮点\"],\"improvementTips\":[\"中文待改进点\"],\"nextActions\":[\"中文下一步行动\"]}。每个数组最多3项；只能基于给定的分数、问题、回答和反馈，不得编造事实。")
                    .userPrompt(buildPrompt(interviewDirection, turns, radarChart, interviewSuggestions, historyContext))
                    .build());
            if (result == null || result.degraded() || StrUtil.isBlank(result.content())) {
                failHarnessRun(runId, "报告模型未返回可用内容", Map.of(
                        "degraded", result != null && result.degraded()));
                return null;
            }
            InterviewReviewFeedbackRespDTO feedback = AgentResponseParser.jsonObject(result.content())
                    .map(this::toFeedback)
                    .filter(this::isValidChineseFeedback)
                    .orElse(null);
            if (feedback == null) {
                failHarnessRun(runId, "报告模型返回内容未通过结构化中文反馈校验", Map.of());
                return null;
            }
            stageHarnessRun(runId, "MODEL_CALL", "AI 面试报告已通过结构化校验", Map.of("resultCount", 1));
            succeedHarnessRun(runId, "AI 面试报告已生成", Map.of("resultCount", 1));
            return feedback;
        } catch (Exception ex) {
            failHarnessRun(runId, "AI 面试报告生成异常", Map.of());
            log.warn("生成 AI 面试报告失败，sessionId={}", sessionId, ex);
            return null;
        }
    }

    private String startHarnessRun(Long userId, String sessionId, List<InterviewTurnLog> turns) {
        if (agentRunCoordinator == null) {
            return null;
        }
        try {
            int turnCount = turns == null ? 0 : turns.size();
            return agentRunCoordinator.start(new AgentRunStartCommand(
                    "INTERVIEW_REPORT", "INTERVIEW_REPORT", userId, sessionId, sessionId, null,
                    false, "面试轮次数=" + turnCount, "historyProjection=true;promptLimit=" + MAX_PROMPT_LENGTH));
        } catch (Exception ex) {
            log.warn("Harness 面试报告运行审计创建失败，继续生成报告。sessionId={}", sessionId, ex);
            return null;
        }
    }

    private void stageHarnessRun(String runId, String stageCode, String message, Map<String, Object> metadata) {
        if (runId == null || agentRunCoordinator == null) {
            return;
        }
        try {
            agentRunCoordinator.stage(runId, stageCode, message, metadata);
        } catch (Exception ex) {
            log.warn("Harness 面试报告阶段审计写入失败，继续生成报告。运行编号={} 阶段={}", runId, stageCode, ex);
        }
    }

    private void succeedHarnessRun(String runId, String resultSummary, Map<String, Object> metadata) {
        if (runId == null || agentRunCoordinator == null) {
            return;
        }
        try {
            agentRunCoordinator.succeed(runId, resultSummary, metadata);
        } catch (Exception ex) {
            log.warn("Harness 面试报告成功审计写入失败。运行编号={}", runId, ex);
        }
    }

    private void failHarnessRun(String runId, String errorSummary, Map<String, Object> metadata) {
        if (runId == null || agentRunCoordinator == null) {
            return;
        }
        try {
            agentRunCoordinator.fail(runId, errorSummary, metadata);
        } catch (Exception ex) {
            log.warn("Harness 面试报告失败审计写入失败。运行编号={}", runId, ex);
        }
    }

    private String buildPrompt(
            String interviewDirection,
            List<InterviewTurnLog> turns,
            RadarChartDTO radarChart,
            String interviewSuggestions) {
        String payload = "岗位方向：" + safe(interviewDirection)
                + "\n能力雷达：" + JSON.toJSONString(radarChart)
                + "\n逐题记录：" + JSON.toJSONString(turns == null ? Collections.emptyList() : turns)
                + "\n已有建议：" + safe(interviewSuggestions);
        return payload.length() <= MAX_PROMPT_LENGTH
                ? payload
                : payload.substring(0, MAX_PROMPT_LENGTH);
    }

    private String buildPrompt(
            String interviewDirection,
            List<InterviewTurnLog> turns,
            RadarChartDTO radarChart,
            String interviewSuggestions,
            InterviewHistoryContext historyContext) {
        String historyText = historyContext == null ? "" : historyContext.toPromptText(2400);
        if (StrUtil.isBlank(historyText)) {
            return buildPrompt(interviewDirection, turns, radarChart, interviewSuggestions);
        }
        int baseLimit = Math.max(0, MAX_PROMPT_LENGTH - historyText.length() - 2);
        String base = buildPrompt(interviewDirection, turns, radarChart, interviewSuggestions);
        if (base.length() > baseLimit) {
            base = base.substring(0, baseLimit);
        }
        return base + "\n\n" + historyText;
    }

    private InterviewReviewFeedbackRespDTO toFeedback(JSONObject response) {
        InterviewReviewFeedbackRespDTO feedback = new InterviewReviewFeedbackRespDTO();
        feedback.setOverallComment(normalize(response.getString("overallComment")));
        feedback.setHighlights(normalizeItems(response.getJSONArray("highlights")));
        feedback.setImprovementTips(normalizeItems(response.getJSONArray("improvementTips")));
        feedback.setNextActions(normalizeItems(response.getJSONArray("nextActions")));
        return feedback;
    }

    private List<String> normalizeItems(JSONArray values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> items = new ArrayList<>();
        for (Object value : values) {
            String item = normalize(value == null ? null : String.valueOf(value));
            if (item != null) {
                items.add(item);
            }
            if (items.size() >= MAX_ITEMS) {
                break;
            }
        }
        return items;
    }

    private boolean isValidChineseFeedback(InterviewReviewFeedbackRespDTO feedback) {
        return feedback != null
                && containsChinese(feedback.getOverallComment())
                && (!feedback.getHighlights().isEmpty()
                || !feedback.getImprovementTips().isEmpty()
                || !feedback.getNextActions().isEmpty());
    }

    private boolean containsChinese(String value) {
        return value != null && value.matches(".*[\\u4e00-\\u9fff].*");
    }

    private String normalize(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
