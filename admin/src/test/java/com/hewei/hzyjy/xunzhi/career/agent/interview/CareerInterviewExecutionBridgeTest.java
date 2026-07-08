package com.hewei.hzyjy.xunzhi.career.agent.interview;

import com.hewei.hzyjy.xunzhi.interview.application.runtime.InterviewSessionRuntimeSnapshotService;
import com.hewei.hzyjy.xunzhi.interview.dao.entity.InterviewSession;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionCacheService;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionService;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewSessionService;
import com.hewei.hzyjy.xunzhi.interview.service.model.InterviewFlowState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareerInterviewExecutionBridgeTest {

    @Test
    void publishesPlanIntoExistingInterviewRuntimeCache() {
        InterviewSessionService sessionService = mock(InterviewSessionService.class);
        InterviewQuestionCacheService cacheService = mock(InterviewQuestionCacheService.class);
        InterviewQuestionService questionService = mock(InterviewQuestionService.class);
        InterviewSessionRuntimeSnapshotService snapshotService = mock(InterviewSessionRuntimeSnapshotService.class);
        InterviewSession session = new InterviewSession();
        session.setSessionId("s1");
        session.setUserId(7L);
        session.setResumeFileUrl("oss://resume.pdf");
        session.setInterviewerAgentId(99L);
        when(sessionService.requireOwnedSession("s1", 7L)).thenReturn(session);
        when(cacheService.getSessionInterviewQuestions("s1")).thenReturn(Map.of(
                "1", "Explain your Redis project.",
                "2", "Please go deeper on Redis: explain the scenario, your responsibility, tradeoffs, and measurable result.",
                "3", "Please go deeper on MySQL: explain the scenario, your responsibility, tradeoffs, and measurable result."
        ));
        InterviewFlowState flowState = new InterviewFlowState();
        flowState.setTotalQuestions(3);
        when(cacheService.getInterviewFlow("s1")).thenReturn(flowState);
        CareerInterviewExecutionBridge bridge = new CareerInterviewExecutionBridge(
                provider(sessionService),
                provider(cacheService),
                provider(questionService),
                provider(snapshotService)
        );
        InterviewPlan plan = plan();

        bridge.publishPlan(7L, "s1", plan);

        verify(sessionService).requireOwnedSession("s1", 7L);
        verify(questionService).upsertStructuredExtraction(eq("s1"), eq(null), eq(99L), eq("oss://resume.pdf"), any(), any(), eq(null), eq("Java backend with Redis focus"), any());
        verify(cacheService).cacheInterviewQuestions(eq("s1"), any());
        verify(cacheService).initInterviewFlow(eq("s1"), eq(3));
        verify(cacheService).cacheInterviewSuggestions(eq("s1"), any());
        verify(cacheService).cacheInterviewDirection("s1", "Java backend with Redis focus");
        verify(sessionService).markReady("s1", 7L, "oss://resume.pdf", "Java backend with Redis focus");
        verify(snapshotService).refreshAfterQuestionExtraction("s1");
    }

    @Test
    void failsClosedWhenExecutionQuestionCacheIsNotReadable() {
        InterviewSessionService sessionService = mock(InterviewSessionService.class);
        InterviewQuestionCacheService cacheService = mock(InterviewQuestionCacheService.class);
        InterviewQuestionService questionService = mock(InterviewQuestionService.class);
        InterviewSession session = new InterviewSession();
        session.setSessionId("s1");
        session.setUserId(7L);
        when(sessionService.requireOwnedSession("s1", 7L)).thenReturn(session);
        when(cacheService.getSessionInterviewQuestions("s1")).thenReturn(Map.of());
        CareerInterviewExecutionBridge bridge = new CareerInterviewExecutionBridge(
                provider(sessionService),
                provider(cacheService),
                provider(questionService),
                emptyProvider()
        );

        assertThrows(IllegalStateException.class, () -> bridge.publishPlan(7L, "s1", plan()));

        verify(cacheService).loadInterviewQuestionsFromDatabase("s1");
        verify(sessionService, never()).markReady(any(), any(), any(), any());
    }

    private InterviewPlan plan() {
        return InterviewPlan.builder()
                .sessionId("s1")
                .firstQuestion("Explain your Redis project.")
                .alignment(JdAlignmentResult.builder()
                        .summary("Java backend with Redis focus")
                        .missingSkills(List.of("Qdrant"))
                        .build())
                .stages(List.of(InterviewStagePlan.builder()
                        .stageName("TECH_DEPTH")
                        .goal("Probe implementation depth")
                        .questionSeeds(List.of("Redis", "MySQL"))
                        .build()))
                .build();
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return provider(null);
    }
}
