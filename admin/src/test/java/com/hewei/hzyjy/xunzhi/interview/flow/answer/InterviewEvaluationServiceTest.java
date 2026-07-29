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
    void preservesHighScoreFollowUpSignalReturnedByScorerWorkflow() throws Exception {
        InterviewQuestionCacheService cacheService = mock(InterviewQuestionCacheService.class);
        InterviewAiInvoker aiInvoker = mock(InterviewAiInvoker.class);
        InterviewHistoryContextProvider historyProvider = mock(InterviewHistoryContextProvider.class);
        AgentPropertiesDO agent = new AgentPropertiesDO();
        String followUpQuestion = "在多轮对话中切换语言时，如何避免历史上下文干扰下一轮输出？";
        when(cacheService.getSessionResumeContext("session-high-score")).thenReturn(Map.of());
        when(historyProvider.load("session-high-score")).thenReturn(InterviewHistoryContext.empty());
        when(aiInvoker.callAiSyncWithParameters(anyString(), eq(agent), anyMap(), anyString(), any()))
                .thenReturn("{\"score\":92,\"logic_ok\":true,\"missing_points\":[],\"feedback\":\"项目过程清晰\",\"follow_up_needed\":true,\"follow_up_question\":\"在多轮对话中切换语言时，如何避免历史上下文干扰下一轮输出？\"}");

        InterviewEvaluationService service = new InterviewEvaluationService(
                cacheService,
                aiInvoker,
                new InterviewResponseParser(),
                historyProvider
        );

        Map<String, Object> result = service.evaluateAnswer(
                "session-high-score", "request-high-score", "1", "介绍多轮对话的实现", "项目过程清晰", agent);

        assertEquals(92, result.get("score"));
        assertTrue(Boolean.TRUE.equals(result.get("follow_up_needed")));
        assertEquals(followUpQuestion, result.get("follow_up_question"));
    }

    @Test
    void scorerWorkflowDeclaresHistoryInputAndHighScoreFollowUpContract() throws Exception {
        String workflow = Files.readString(
                Path.of("src/main/resources/workflow/用户答案评分官.yml"),
                StandardCharsets.UTF_8
        );

        int startNodeIndex = workflow.indexOf("id: node-start::d61b0f71-87ee-475e-93ba-f1607f0ce783");
        int endNodeIndex = workflow.indexOf("id: node-end::cda617af-551e-462e-b3b8-3bb9a041bf88");
        int llmNodeIndex = workflow.indexOf("id: spark-llm::3d610bd9-1432-4746-96dc-384cafb89313");
        String startNode = workflow.substring(startNodeIndex, endNodeIndex);
        String llmNode = workflow.substring(llmNodeIndex, workflow.indexOf("  edges:", llmNodeIndex));
        String llmOutputs = llmNode.substring(llmNode.indexOf("      outputs:"), llmNode.indexOf("      nodeParam:"));

        assertTrue(startNode.contains("name: interview_history_context"));
        assertTrue(llmNode.contains("name: interview_history_context"));
        assertTrue(llmNode.contains("id: 6f21e9b4-f816-4f4c-b80e-6c5e3d2cf0e1"));
        assertTrue(llmNode.contains("<interview_history_context>{{interview_history_context}}</interview_history_context>"));
        assertEquals(6, countOccurrences(llmOutputs, "        name: "));
        assertTrue(llmOutputs.contains("name: score"));
        assertTrue(llmOutputs.contains("name: logic_ok"));
        assertTrue(llmOutputs.contains("name: missing_points"));
        assertTrue(llmOutputs.contains("name: feedback"));
        assertTrue(llmOutputs.contains("name: follow_up_needed"));
        assertTrue(llmOutputs.contains("name: follow_up_question"));
        assertFalse(llmOutputs.contains("follow_up_type"));
        assertFalse(llmOutputs.contains("follow_up_focus"));
        assertFalse(llmOutputs.contains("follow_up_reason"));
        assertTrue(workflow.contains("score 仅评价当前回答质量"));
        assertTrue(workflow.contains("即使 score >= 90"));
        assertTrue(workflow.contains("follow_up_question 必须是围绕一个单一能力点"));
        assertTrue(workflow.contains("禁止“请详细说明”"));
        assertTrue(workflow.contains("不得因为高分而输出“无”"));
        assertTrue(workflow.contains("不能因为历史中的高分而压制当前回答仍然值得继续的追问"));
        assertTrue(workflow.contains("\"follow_up_needed\": true, \"follow_up_question\": \"请说明角色、资源范围与权限校验链路"));
    }

    @Test
    void sendsHistoryProjectionToScorerWorkflow() throws Exception {
        InterviewQuestionCacheService cacheService = mock(InterviewQuestionCacheService.class);
        InterviewAiInvoker aiInvoker = mock(InterviewAiInvoker.class);
        InterviewHistoryContextProvider historyProvider = mock(InterviewHistoryContextProvider.class);
        AgentPropertiesDO agent = new AgentPropertiesDO();
        InterviewHistoryContext historyContext = new InterviewHistoryContext(
                true, 5L, 3L, List.of("1"), List.of(), List.of(), List.of(), List.of("2"));
        when(cacheService.getSessionResumeContext("session-1")).thenReturn(Map.of("summary", "Java backend developer"));
        when(historyProvider.load("session-1")).thenReturn(historyContext);
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
        assertTrue(parameters.getValue().containsKey("interview_history_context"));
        assertEquals(historyContext.toPromptText(1400), parameters.getValue().get("interview_history_context"));
        assertEquals("Java backend developer", parameters.getValue().get("resume_context"));
    }

    @Test
    void continuesScoringWithEmptyHistoryWhenHistoryProviderThrows() throws Exception {
        InterviewQuestionCacheService cacheService = mock(InterviewQuestionCacheService.class);
        InterviewAiInvoker aiInvoker = mock(InterviewAiInvoker.class);
        InterviewHistoryContextProvider historyProvider = mock(InterviewHistoryContextProvider.class);
        AgentPropertiesDO agent = new AgentPropertiesDO();
        when(cacheService.getSessionResumeContext("session-history-error")).thenReturn(Map.of());
        when(historyProvider.load("session-history-error")).thenThrow(new IllegalStateException("history store timeout"));
        when(aiInvoker.callAiSyncWithParameters(anyString(), eq(agent), anyMap(), anyString(), any()))
                .thenReturn("{\"score\":80,\"logic_ok\":true,\"missing_points\":[],\"feedback\":\"good\",\"follow_up_needed\":false,\"follow_up_question\":\"\"}");

        InterviewEvaluationService service = new InterviewEvaluationService(
                cacheService, aiInvoker, new InterviewResponseParser(), historyProvider);

        Map<String, Object> result = service.evaluateAnswer(
                "session-history-error", "request-history-error", "1", "Explain Redis", "My answer", agent);

        assertEquals(80, result.get("score"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> parameters = ArgumentCaptor.forClass(Map.class);
        verify(aiInvoker).callAiSyncWithParameters(anyString(), eq(agent), parameters.capture(), anyString(), any());
        assertEquals(InterviewHistoryContext.empty().toPromptText(1400),
                parameters.getValue().get("interview_history_context"));
    }

    @Test
    void continuesScoringWithEmptyHistoryWhenHistoryProviderReturnsNull() throws Exception {
        InterviewQuestionCacheService cacheService = mock(InterviewQuestionCacheService.class);
        InterviewAiInvoker aiInvoker = mock(InterviewAiInvoker.class);
        InterviewHistoryContextProvider historyProvider = mock(InterviewHistoryContextProvider.class);
        AgentPropertiesDO agent = new AgentPropertiesDO();
        when(cacheService.getSessionResumeContext("session-history-null")).thenReturn(Map.of());
        when(historyProvider.load("session-history-null")).thenReturn(null);
        when(aiInvoker.callAiSyncWithParameters(anyString(), eq(agent), anyMap(), anyString(), any()))
                .thenReturn("{\"score\":81,\"logic_ok\":true,\"missing_points\":[],\"feedback\":\"good\",\"follow_up_needed\":false,\"follow_up_question\":\"\"}");

        InterviewEvaluationService service = new InterviewEvaluationService(
                cacheService, aiInvoker, new InterviewResponseParser(), historyProvider);

        Map<String, Object> result = service.evaluateAnswer(
                "session-history-null", "request-history-null", "1", "Explain Redis", "My answer", agent);

        assertEquals(81, result.get("score"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> parameters = ArgumentCaptor.forClass(Map.class);
        verify(aiInvoker).callAiSyncWithParameters(anyString(), eq(agent), parameters.capture(), anyString(), any());
        assertEquals(InterviewHistoryContext.empty().toPromptText(1400),
                parameters.getValue().get("interview_history_context"));
    }

    private static int countOccurrences(String text, String fragment) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }
        return count;
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
