package com.hewei.hzyjy.xunzhi.interview.flow.answer;

import com.hewei.hzyjy.xunzhi.agent.dao.entity.AgentPropertiesDO;
import com.hewei.hzyjy.xunzhi.interview.application.history.InterviewHistoryContext;
import com.hewei.hzyjy.xunzhi.interview.application.history.InterviewHistoryContextProvider;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionCacheService;
import com.hewei.hzyjy.xunzhi.interview.shared.InterviewAiInvoker;
import com.hewei.hzyjy.xunzhi.interview.shared.InterviewResponseParser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewEvaluationServiceTest {

    @Test
    void scorerWorkflowDoesNotDeclareHistoryInputInVersionedBaseline() throws Exception {
        String workflow = Files.readString(
                Path.of("src/main/resources/workflow/用户答案评分官.yml"),
                StandardCharsets.UTF_8
        );

        assertFalse(workflow.contains("name: interview_history_context"));
        assertFalse(workflow.contains("面试历史事实"));
    }

    @Test
    void doesNotSendHistoryProjectionToCurrentScorerWorkflow() throws Exception {
        InterviewQuestionCacheService cacheService = mock(InterviewQuestionCacheService.class);
        InterviewAiInvoker aiInvoker = mock(InterviewAiInvoker.class);
        InterviewHistoryContextProvider historyProvider = mock(InterviewHistoryContextProvider.class);
        AgentPropertiesDO agent = new AgentPropertiesDO();
        when(cacheService.getSessionResumeContext("session-1")).thenReturn(Map.of("summary", "Java backend developer"));
        when(historyProvider.load("session-1")).thenReturn(new InterviewHistoryContext(
                true, 5L, 3L, List.of("1"), List.of(), List.of(), List.of(), List.of("2")));
        when(aiInvoker.callAiSyncWithParameters(anyString(), eq(agent), anyMap(), anyString(), any()))
                .thenReturn("{\"score\":80,\"logic_ok\":true,\"missing_points\":[],\"feedback\":\"good\",\"follow_up_needed\":false,\"follow_up_question\":\"\"}");

        InterviewEvaluationService service = new InterviewEvaluationService(
                cacheService,
                aiInvoker,
                new InterviewResponseParser(),
                historyProvider
        );

        service.evaluateAnswer("session-1", "request-1", "2", "Explain Redis", "My answer", agent);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> parameters = ArgumentCaptor.forClass(Map.class);
        verify(aiInvoker).callAiSyncWithParameters(anyString(), eq(agent), parameters.capture(), anyString(), any());
        assertFalse(parameters.getValue().containsKey("interview_history_context"));
        assertEquals("Java backend developer", parameters.getValue().get("resume_context"));
    }

    @Test
    void retriesScorerWorkflowWithCompleteParametersWhenResponseDoesNotContainScore() throws Exception {
        InterviewQuestionCacheService cacheService = mock(InterviewQuestionCacheService.class);
        InterviewAiInvoker aiInvoker = mock(InterviewAiInvoker.class);
        InterviewHistoryContextProvider historyProvider = mock(InterviewHistoryContextProvider.class);
        AgentPropertiesDO agent = new AgentPropertiesDO();
        when(cacheService.getSessionResumeContext("session-2")).thenReturn(Map.of());
        when(historyProvider.load("session-2")).thenReturn(InterviewHistoryContext.empty());
        when(aiInvoker.callAiSyncWithParameters(anyString(), eq(agent), anyMap(), anyString(), any()))
                .thenReturn(
                        "{\"feedback\":\"workflow omitted score\",\"missing_points\":[]}",
                        "{\"score\":76,\"feedback\":\"good\",\"follow_up_needed\":false,\"follow_up_question\":\"\",\"missing_points\":[]}"
                );

        InterviewEvaluationService service = new InterviewEvaluationService(
                cacheService,
                aiInvoker,
                new InterviewResponseParser(),
                historyProvider
        );

        Map<String, Object> result = service.evaluateAnswer(
                "session-2", "request-2", "1", "Explain Redis", "My answer", agent);

        assertEquals(76, result.get("score"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> parameters = ArgumentCaptor.forClass(Map.class);
        verify(aiInvoker, times(2)).callAiSyncWithParameters(
                eq("session-2_score"), eq(agent), parameters.capture(), anyString(), any());
        assertEquals("Explain Redis", parameters.getAllValues().get(1).get("question"));
        assertEquals("My answer", parameters.getAllValues().get(1).get("AGENT_USER_INPUT"));
    }

    @Test
    void usesConservativeLocalScoreWhenBothAiResponsesOmitScore() throws Exception {
        InterviewQuestionCacheService cacheService = mock(InterviewQuestionCacheService.class);
        InterviewAiInvoker aiInvoker = mock(InterviewAiInvoker.class);
        InterviewHistoryContextProvider historyProvider = mock(InterviewHistoryContextProvider.class);
        AgentPropertiesDO agent = new AgentPropertiesDO();
        when(cacheService.getSessionResumeContext("session-3")).thenReturn(Map.of());
        when(historyProvider.load("session-3")).thenReturn(InterviewHistoryContext.empty());
        when(aiInvoker.callAiSyncWithParameters(anyString(), eq(agent), anyMap(), anyString(), any()))
                .thenReturn("{\"feedback\":\"workflow omitted score\"}", "{\"feedback\":\"retry omitted score\"}");

        InterviewEvaluationService service = new InterviewEvaluationService(
                cacheService,
                aiInvoker,
                new InterviewResponseParser(),
                historyProvider
        );

        Map<String, Object> result = service.evaluateAnswer(
                "session-3",
                "request-3",
                "1",
                "Explain Redis",
                "I would discuss cache consistency, persistence, distributed locks, and failure recovery.",
                agent
        );

        assertNotNull(result.get("score"));
        assertTrue(String.valueOf(result.get("feedback")).contains("\u672c\u5730"));
    }

    @Test
    void rejectsMissingInputPlaceholderEvenWhenItContainsZeroScore() throws Exception {
        InterviewQuestionCacheService cacheService = mock(InterviewQuestionCacheService.class);
        InterviewAiInvoker aiInvoker = mock(InterviewAiInvoker.class);
        InterviewHistoryContextProvider historyProvider = mock(InterviewHistoryContextProvider.class);
        AgentPropertiesDO agent = new AgentPropertiesDO();
        when(cacheService.getSessionResumeContext("session-4")).thenReturn(Map.of());
        when(historyProvider.load("session-4")).thenReturn(InterviewHistoryContext.empty());
        when(aiInvoker.callAiSyncWithParameters(anyString(), eq(agent), anyMap(), anyString(), any()))
                .thenReturn(
                        "{\"score\":0,\"feedback\":\"缺少评分所需的核心内容，无法完成维度评估\",\"missing_points\":[\"未提供需要评分的面试题目和答案\"],\"follow_up_needed\":true,\"follow_up_question\":\"请提供技术面试题目及对应的面试者答案文本\"}",
                        "{\"score\":82,\"feedback\":\"回答覆盖了核心知识点\",\"follow_up_needed\":false,\"follow_up_question\":\"\",\"missing_points\":[]}"
                );

        InterviewEvaluationService service = new InterviewEvaluationService(
                cacheService,
                aiInvoker,
                new InterviewResponseParser(),
                historyProvider
        );

        Map<String, Object> result = service.evaluateAnswer(
                "session-4",
                "request-4",
                "1",
                "Explain Redis consistency",
                "I would use delayed double delete and idempotent consumers to reduce stale-cache risk.",
                agent
        );

        assertEquals(82, result.get("score"));
        assertFalse(Boolean.TRUE.equals(result.get("follow_up_needed")));
        verify(aiInvoker, times(2)).callAiSyncWithParameters(
                eq("session-4_score"), eq(agent), anyMap(), anyString(), any());
    }
}
