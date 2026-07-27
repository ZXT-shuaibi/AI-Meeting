package com.hewei.hzyjy.xunzhi.career.resume.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryJobMatchTaskStoreTest {

    @Test
    void historyRetainsRequestedTopKAndReturnedCandidates() {
        InMemoryJobMatchTaskStore store = new InMemoryJobMatchTaskStore();
        JobMatchCandidate candidate = new JobMatchCandidate(101L, "resume.pdf", 1, 95,
                "推荐", List.of("Java"), List.of());
        JobMatchTaskResult task = JobMatchTaskResult.completed("task-1", 7L,
                List.of(101L, 102L), List.of(candidate), List.of("evidence"));

        store.save(task, "Java backend JD", 5, null);

        JobMatchHistoryItem item = store.findRecentByUserId(7L, 20).getFirst();
        assertEquals(5, item.requestedTopK());
        assertEquals(List.of(101L, 102L), item.selectedResumeIds());
        assertEquals(1, item.matchedResumes().size());
        assertEquals(101L, item.matchedResumes().getFirst().resumeId());
    }
}
