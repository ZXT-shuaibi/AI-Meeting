package com.hewei.hzyjy.xunzhi.interview.flow.report;

import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.InterviewReviewFeedbackRespDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.RadarChartDTO;
import com.hewei.hzyjy.xunzhi.interview.service.model.InterviewTurnLog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewReportAiReviewerTest {

    @Test
    void shouldBuildChineseStructuredFeedbackFromAiResponse() {
        AiGateway aiGateway = mock(AiGateway.class);
        when(aiGateway.chat(any())).thenReturn(AiGatewayResult.builder()
                .content("""
                        {"overallComment":"整体表现达到岗位基本要求，建议加强回答的结构化表达。","highlights":["简历匹配度较高"],"improvementTips":["回答中补充具体的项目指标"],"nextActions":["使用 STAR 结构复盘核心项目"]}
                        """)
                .degraded(false)
                .build());
        InterviewReportAiReviewer reviewer = new InterviewReportAiReviewer(aiGateway);
        RadarChartDTO radarChart = new RadarChartDTO();
        radarChart.setResumeScore(92);
        radarChart.setInterviewPerformance(25);

        InterviewReviewFeedbackRespDTO feedback = reviewer.review(
                "session-1",
                "后端开发实习生",
                List.of(InterviewTurnLog.builder()
                        .questionContent("如何优化慢 SQL？")
                        .answerContent("我会先定位慢查询。")
                        .score(25)
                        .feedback("缺少索引和执行计划分析。")
                        .build()),
                radarChart,
                "补充项目量化成果"
        );

        assertNotNull(feedback);
        assertEquals("整体表现达到岗位基本要求，建议加强回答的结构化表达。", feedback.getOverallComment());
        assertEquals(List.of("简历匹配度较高"), feedback.getHighlights());
        assertEquals(List.of("回答中补充具体的项目指标"), feedback.getImprovementTips());
    }
}
