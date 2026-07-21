package com.hewei.hzyjy.xunzhi.interview.flow.report;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hewei.hzyjy.xunzhi.career.agent.support.AgentResponseParser;
import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewReportAiReviewer {

    private static final int MAX_ITEMS = 3;
    private static final int MAX_PROMPT_LENGTH = 14_000;

    private final AiGateway aiGateway;
    private InterviewHistoryContextProvider interviewHistoryContextProvider;

    @Autowired
    void setInterviewHistoryContextProvider(InterviewHistoryContextProvider interviewHistoryContextProvider) {
        this.interviewHistoryContextProvider = interviewHistoryContextProvider;
    }

    public InterviewReviewFeedbackRespDTO review(
            String sessionId,
            String interviewDirection,
            List<InterviewTurnLog> turns,
            RadarChartDTO radarChart,
            String interviewSuggestions) {
        try {
            InterviewHistoryContext historyContext = interviewHistoryContextProvider == null
                    ? InterviewHistoryContext.empty()
                    : interviewHistoryContextProvider.load(sessionId);
            AiGatewayResult result = aiGateway.chat(AiPromptRequest.builder()
                    .sceneCode("interview-report-review")
                    .sessionId(sessionId)
                    .systemPrompt("你是一名资深中文面试教练。根据提供的面试事实生成个性化复盘。只输出合法 JSON，禁止 Markdown 和额外说明。格式固定为：{\"overallComment\":\"不超过120字的中文总体评价\",\"highlights\":[\"中文亮点\"],\"improvementTips\":[\"中文待改进点\"],\"nextActions\":[\"中文下一步行动\"]}。每个数组最多3项；只能基于给定的分数、问题、回答和反馈，不得编造事实。")
                    .userPrompt(buildPrompt(interviewDirection, turns, radarChart, interviewSuggestions, historyContext))
                    .build());
            if (result == null || result.degraded() || StrUtil.isBlank(result.content())) {
                return null;
            }
            return AgentResponseParser.jsonObject(result.content())
                    .map(this::toFeedback)
                    .filter(this::isValidChineseFeedback)
                    .orElse(null);
        } catch (Exception ex) {
            log.warn("生成 AI 面试报告失败，sessionId={}", sessionId, ex);
            return null;
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
