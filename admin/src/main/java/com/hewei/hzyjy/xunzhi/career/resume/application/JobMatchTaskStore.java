package com.hewei.hzyjy.xunzhi.career.resume.application;

import java.util.List;
import java.util.Optional;

public interface JobMatchTaskStore {

    JobMatchTaskResult save(JobMatchTaskResult task, String jobDescription, int limit, String errorMessage);

    Optional<JobMatchTaskResult> findByTaskId(String taskId);

    default List<JobMatchHistoryItem> findRecentByUserId(Long userId, int limit) {
        return List.of();
    }

    default long countByUserId(Long userId) {
        return 0L;
    }

    default Optional<JobMatchTaskResult> findByTaskIdAndUserId(String taskId, Long userId) {
        if (taskId == null || taskId.isBlank() || userId == null) {
            return Optional.empty();
        }
        return findByTaskId(taskId).filter(task -> userId.equals(task.userId()));
    }
}
