package com.hewei.hzyjy.xunzhi.career.resume.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ResumeParseTaskStore {

    ResumeParseTaskRecord save(ResumeParseTaskRecord task);

    Optional<ResumeParseTaskRecord> findByTaskId(String taskId);

    Optional<ResumeParseTaskRecord> findByTaskIdAndUserId(String taskId, Long userId);

    List<ResumeParseTaskRecord> findByUserId(Long userId, String status);

    void updateDisplayOrder(Long userId, List<Long> resumeIds);

    List<ResumeParseTaskRecord> findStaleActiveTasks(Instant updatedBefore, int limit);
}
