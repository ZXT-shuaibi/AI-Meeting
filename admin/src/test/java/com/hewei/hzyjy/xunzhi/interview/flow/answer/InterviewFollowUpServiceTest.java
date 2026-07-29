package com.hewei.hzyjy.xunzhi.interview.flow.answer;

import com.hewei.hzyjy.xunzhi.agent.application.BusinessAgentResolver;
import com.hewei.hzyjy.xunzhi.agent.application.BusinessAgentScene;
import com.hewei.hzyjy.xunzhi.agent.dao.entity.AgentPropertiesDO;
import com.hewei.hzyjy.xunzhi.interview.application.history.InterviewHistoryContext;
import com.hewei.hzyjy.xunzhi.interview.application.history.InterviewHistoryContextProvider;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionCacheService;
import com.hewei.hzyjy.xunzhi.interview.shared.InterviewAiInvoker;
import com.hewei.hzyjy.xunzhi.interview.shared.InterviewResponseParser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewFollowUpServiceTest {

    @Test
    void sendsHistoryProjectionAndScorerSuggestedQuestionToFollowUpWorkflow() throws Exception {
        BusinessAgentResolver resolver = mock(BusinessAgentResolver.class);
        InterviewQuestionCacheService cacheService = mock(InterviewQuestionCacheService.class);
        InterviewAiInvoker aiInvoker = mock(InterviewAiInvoker.class);
        InterviewHistoryContextProvider historyProvider = mock(InterviewHistoryContextProvider.class);
        AgentPropertiesDO agent = new AgentPropertiesDO();
        when(resolver.resolveRequired(BusinessAgentScene.INTERVIEW_QUESTION_ASKING)).thenReturn(agent);
        when(cacheService.getSessionResumeContext("session-1")).thenReturn(Map.of("summary", "Java backend developer"));
        when(historyProvider.load("session-1")).thenReturn(new InterviewHistoryContext(
                true, 5L, 3L, List.of("1"), List.of(), List.of(), List.of(), List.of("2")));
        when(aiInvoker.callAiSyncWithParameters(anyString(), eq(agent), anyMap(), anyString(), any()))
                .thenReturn("{\"ask_to_user\":\"请说明事务隔离级别的具体影响？\",\"end_interview\":false}");

        InterviewFollowUpService service = new InterviewFollowUpService(
                resolver,
                cacheService,
                aiInvoker,
                new InterviewResponseParser(),
                historyProvider
        );

        String suggestedQuestion = "在发生主从延迟时，你如何验证读写一致性没有被破坏？";
        InterviewFollowUpService.FollowUpQuestionResult result = service.generateFollowUpQuestion(
                "session-1", "request-1", "1", "Explain transactions", "My answer", suggestedQuestion, 0, 4);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> parameters = ArgumentCaptor.forClass(Map.class);
        verify(aiInvoker).callAiSyncWithParameters(eq("session-1"), eq(agent), parameters.capture(), anyString(), any());
        assertTrue(result.hasQuestion());
        assertEquals(suggestedQuestion, parameters.getValue().get("follow_up_question"));
        assertEquals(new InterviewHistoryContext(
                true, 5L, 3L, List.of("1"), List.of(), List.of(), List.of(), List.of("2"))
                        .toPromptText(1400),
                parameters.getValue().get("interview_history_context"));
        assertFalse(String.valueOf(parameters.getValue().get("resume_context")).contains("uncovered_question_numbers"));
    }

    @Test
    void fallsBackToScorerSuggestedQuestionWhenRemoteGeneratorEndsInterview() throws Exception {
        BusinessAgentResolver resolver = mock(BusinessAgentResolver.class);
        InterviewQuestionCacheService cacheService = mock(InterviewQuestionCacheService.class);
        InterviewAiInvoker aiInvoker = mock(InterviewAiInvoker.class);
        InterviewHistoryContextProvider historyProvider = mock(InterviewHistoryContextProvider.class);
        AgentPropertiesDO agent = new AgentPropertiesDO();
        String suggestedQuestion = "在发生主从延迟时，你如何验证读写一致性没有被破坏？";
        when(resolver.resolveRequired(BusinessAgentScene.INTERVIEW_QUESTION_ASKING)).thenReturn(agent);
        when(cacheService.getSessionResumeContext("session-fallback")).thenReturn(Map.of());
        when(historyProvider.load("session-fallback")).thenReturn(InterviewHistoryContext.empty());
        when(aiInvoker.callAiSyncWithParameters(anyString(), eq(agent), anyMap(), anyString(), any()))
                .thenReturn("{\"end_interview\":true,\"ask_to_user\":\"\"}");

        InterviewFollowUpService service = new InterviewFollowUpService(
                resolver, cacheService, aiInvoker, new InterviewResponseParser(), historyProvider);

        InterviewFollowUpService.FollowUpQuestionResult result = service.generateFollowUpQuestion(
                "session-fallback", "request-fallback", "1", "Explain transactions", "My answer", suggestedQuestion, 0, 4);

        assertTrue(result.hasQuestion());
        assertEquals("1-F1", result.getQuestionNumber());
        assertEquals(1, result.getFollowUpCount());
        assertEquals(suggestedQuestion, result.getQuestionContent());
    }

    @Test
    void treatsChineseNoFollowUpAsNoQuestion() throws Exception {
        BusinessAgentResolver resolver = mock(BusinessAgentResolver.class);
        InterviewQuestionCacheService cacheService = mock(InterviewQuestionCacheService.class);
        InterviewAiInvoker aiInvoker = mock(InterviewAiInvoker.class);
        InterviewHistoryContextProvider historyProvider = mock(InterviewHistoryContextProvider.class);
        AgentPropertiesDO agent = new AgentPropertiesDO();
        when(resolver.resolveRequired(BusinessAgentScene.INTERVIEW_QUESTION_ASKING)).thenReturn(agent);
        when(historyProvider.load("session-2")).thenReturn(InterviewHistoryContext.empty());
        when(aiInvoker.callAiSyncWithParameters(anyString(), eq(agent), anyMap(), anyString(), any()))
                .thenReturn("{\"ask_to_user\":\"\\u65e0\",\"end_interview\":false}");

        InterviewFollowUpService service = new InterviewFollowUpService(
                resolver,
                cacheService,
                aiInvoker,
                new InterviewResponseParser(),
                historyProvider
        );

        InterviewFollowUpService.FollowUpQuestionResult result = service.generateFollowUpQuestion(
                "session-2", "request-2", "1", "Explain transactions", "My answer", null, 0, 2);

        assertFalse(result.hasQuestion());
    }
}
