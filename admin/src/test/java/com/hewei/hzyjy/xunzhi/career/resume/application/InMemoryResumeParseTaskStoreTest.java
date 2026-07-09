package com.hewei.hzyjy.xunzhi.career.resume.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryResumeParseTaskStoreTest {

    @Test
    void findsOnlyStaleActiveTasksOrderedByUpdateTime() {
        InMemoryResumeParseTaskStore store = new InMemoryResumeParseTaskStore();
        Instant now = Instant.parse("2026-07-09T06:00:00Z");
        ResumeParseTaskRecord olderActive = task("older", ResumeParseTaskStatus.ANALYZING, now.minusSeconds(7200));
        ResumeParseTaskRecord newerActive = task("newer", ResumeParseTaskStatus.SAVING, now.minusSeconds(3600));
        ResumeParseTaskRecord freshActive = task("fresh", ResumeParseTaskStatus.PROCESSING, now.minusSeconds(60));
        ResumeParseTaskRecord failed = task("failed", ResumeParseTaskStatus.FAILED, now.minusSeconds(7200));
        store.save(newerActive);
        store.save(failed);
        store.save(freshActive);
        store.save(olderActive);

        List<ResumeParseTaskRecord> stale = store.findStaleActiveTasks(now.minusSeconds(600), 10);

        assertThat(stale).extracting(ResumeParseTaskRecord::taskId).containsExactly("older", "newer");
    }

    private ResumeParseTaskRecord task(String taskId, ResumeParseTaskStatus status, Instant updateTime) {
        return new ResumeParseTaskRecord(
                taskId,
                7L,
                status,
                null,
                "resume.txt",
                4L,
                "text/plain",
                "upload",
                "local-snapshot",
                taskId,
                null,
                "Java".getBytes(StandardCharsets.UTF_8),
                null,
                null,
                updateTime.minusSeconds(60),
                status.terminal() ? updateTime : null,
                updateTime.minusSeconds(120),
                updateTime
        );
    }
}
