package com.hewei.hzyjy.xunzhi.interview.flow.report;

import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.harness.application.AgentRunCoordinator;
import com.hewei.hzyjy.xunzhi.career.harness.model.AgentRunStartCommand;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.RadarChartDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class InterviewReportHarnessIntegrationTest {

    @Test
    void recordsHistoryAndModelStagesWithoutWritingInterviewContentIntoRunSummary() {
        AiGateway aiGateway = mock(AiGateway.class);
        AgentRunCoordinator coordinator = mock(AgentRunCoordinator.class);
        when(aiGateway.chat(any())).thenReturn(AiGatewayResult.builder()
                .content("{\"overallComment\":\"整体表达清晰\",\"highlights\":[\"项目描述完整\"],\"improvementTips\":[],\"nextActions\":[]}")
                .provider("workflow")
                .model("report-flow")
                .degraded(false)
                .build());
        when(coordinator.start(any())).thenReturn("agent-run-report-1");
        InterviewReportAiReviewer reviewer = new InterviewReportAiReviewer(aiGateway);
        reviewer.setAgentRunCoordinator(coordinator);

        var feedback = reviewer.review(7L, "session-1", "后端开发", List.of(), new RadarChartDTO(), "不要把这段建议原文写入审计摘要");

        assertNotNull(feedback);
        ArgumentCaptor<AgentRunStartCommand> commandCaptor = ArgumentCaptor.forClass(AgentRunStartCommand.class);
        verify(coordinator).start(commandCaptor.capture());
        assertEquals(7L, commandCaptor.getValue().userId());
        assertEquals("INTERVIEW_REPORT", commandCaptor.getValue().sceneCode());
        assertFalse(commandCaptor.getValue().inputSummary().contains("不要把这段"));
        verify(coordinator, atLeastOnce()).stage(eq("agent-run-report-1"), eq("HISTORY_CONTEXT"), anyString(), anyMap());
        verify(coordinator, atLeastOnce()).stage(eq("agent-run-report-1"), eq("MODEL_CALL"), anyString(), anyMap());
        verify(coordinator).succeed(eq("agent-run-report-1"), anyString(), anyMap());
    }
}
