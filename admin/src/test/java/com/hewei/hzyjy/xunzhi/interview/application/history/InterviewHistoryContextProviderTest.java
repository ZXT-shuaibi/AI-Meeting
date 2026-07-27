package com.hewei.hzyjy.xunzhi.interview.application.history;

import com.hewei.hzyjy.xunzhi.interview.application.runtime.InterviewSessionRuntimeSnapshotService;
import com.hewei.hzyjy.xunzhi.interview.dao.entity.InterviewSessionRuntimeSnapshot;
import com.hewei.hzyjy.xunzhi.interview.service.model.InterviewTurnLog;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewHistoryContextProviderTest {

    @Test
    void projectsRecentTurnsLowScoreEvidenceAndFollowUpChains() {
        InterviewSessionRuntimeSnapshotService snapshotService = mock(InterviewSessionRuntimeSnapshotService.class);
        InterviewSessionRuntimeSnapshot snapshot = snapshot(12L, 3L);
        when(snapshotService.findSnapshot("session-1")).thenReturn(Optional.of(snapshot));
        when(snapshotService.loadPersistedTurns("session-1")).thenReturn(List.of(
                turn("1", "Explain Redis cache eviction", "I would use TTL", 86, false, null),
                turn("2", "Explain database transactions", "I only know ACID", 42, false, "2-F1"),
                turn("2-F1", "Explain transaction isolation", "Not sure", 38, true, null)
        ));

        InterviewHistoryContext context = new InterviewHistoryContextProvider(snapshotService).load("session-1");

        assertTrue(context.available());
        assertEquals(List.of("1", "2", "2-F1"), context.assessedQuestionNumbers());
        assertEquals(1, context.lowScoreFindings().size());
        assertEquals("2", context.lowScoreFindings().getFirst().questionNumber());
        assertEquals(1, context.followUpChains().size());
        assertEquals(List.of("3"), context.uncoveredQuestionNumbers());
    }

    @Test
    void redactsSensitiveValuesAndBoundsRenderedPrompt() {
        InterviewSessionRuntimeSnapshotService snapshotService = mock(InterviewSessionRuntimeSnapshotService.class);
        when(snapshotService.findSnapshot("session-1")).thenReturn(Optional.of(snapshot(1L, 1L)));
        when(snapshotService.loadPersistedTurns("session-1")).thenReturn(List.of(
                turn("1", "Contact details", "Call 13800138000 or candidate@example.com for the answer", 60, false, null)
        ));

        String prompt = new InterviewHistoryContextProvider(snapshotService).load("session-1").toPromptText(300);

        assertTrue(prompt.length() <= 300);
        assertFalse(prompt.contains("13800138000"));
        assertFalse(prompt.contains("candidate@example.com"));
        assertTrue(prompt.contains("recent_turns"));
    }

    @Test
    void returnsEmptyContextWhenHistoryReadFails() {
        InterviewSessionRuntimeSnapshotService snapshotService = mock(InterviewSessionRuntimeSnapshotService.class);
        when(snapshotService.findSnapshot("session-1")).thenThrow(new IllegalStateException("mongo unavailable"));

        InterviewHistoryContext context = new InterviewHistoryContextProvider(snapshotService).load("session-1");

        assertFalse(context.available());
        assertEquals("", context.toPromptText(300));
    }

    private static InterviewSessionRuntimeSnapshot snapshot(Long version, Long watermark) {
        InterviewSessionRuntimeSnapshot snapshot = new InterviewSessionRuntimeSnapshot();
        snapshot.setSnapshotVersion(version);
        snapshot.setArchiveWatermark(watermark);
        LinkedHashMap<String, String> questions = new LinkedHashMap<>();
        questions.put("1", "Explain Redis cache eviction");
        questions.put("2", "Explain database transactions");
        questions.put("3", "Explain JVM garbage collection");
        snapshot.setQuestions(questions);
        return snapshot;
    }

    private static InterviewTurnLog turn(
            String questionNumber,
            String question,
            String answer,
            int score,
            boolean followUp,
            String nextQuestionNumber) {
        return InterviewTurnLog.builder()
                .questionNumber(questionNumber)
                .questionContent(question)
                .answerContent(answer)
                .score(score)
                .feedback("Feedback for " + questionNumber)
                .isFollowUp(followUp)
                .followUpNeeded(nextQuestionNumber != null)
                .nextQuestionNumber(nextQuestionNumber)
                .build();
    }
}
