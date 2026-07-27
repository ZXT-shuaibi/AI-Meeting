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
    void doesNotSendHistoryProjectionToCurrentFollowUpWorkflow() throws Exception {
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

        InterviewFollowUpService.FollowUpQuestionResult result = service.generateFollowUpQuestion(
                "session-1", "request-1", "1", "Explain transactions", "My answer", null, 0, 2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> parameters = ArgumentCaptor.forClass(Map.class);
        verify(aiInvoker).callAiSyncWithParameters(eq("session-1"), eq(agent), parameters.capture(), anyString(), any());
        assertTrue(result.hasQuestion());
        assertFalse(parameters.getValue().containsKey("interview_history_context"));
        assertFalse(String.valueOf(parameters.getValue().get("resume_context")).contains("uncovered_question_numbers"));
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
