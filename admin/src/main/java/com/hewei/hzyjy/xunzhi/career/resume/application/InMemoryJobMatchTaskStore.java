package com.hewei.hzyjy.xunzhi.career.resume.application;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryJobMatchTaskStore implements JobMatchTaskStore {

    private final Map<String, StoredTask> tasks = new ConcurrentHashMap<>();

    @Override
    public JobMatchTaskResult save(JobMatchTaskResult task, String jobDescription, int limit, String errorMessage) {
        tasks.put(task.taskId(), new StoredTask(task, jobDescription, limit));
        return task;
    }

    @Override
    public Optional<JobMatchTaskResult> findByTaskId(String taskId) {
        return Optional.ofNullable(tasks.get(taskId)).map(StoredTask::task);
    }

    @Override
    public List<JobMatchHistoryItem> findRecentByUserId(Long userId, int limit) {
        return tasks.values().stream()
                .filter(stored -> userId != null && userId.equals(stored.task().userId()))
                .limit(limit <= 0 ? Long.MAX_VALUE : limit)
                .map(stored -> {
                    JobMatchTaskResult task = stored.task();
                    return new JobMatchHistoryItem(task.taskId(), task.status(), stored.jobDescription(), task.selectedResumeIds().size(),
                            task.selectedResumeIds(), stored.requestedTopK(), task.matchedResumes(), task.errorMessage(), null);
                })
                .toList();
    }

    @Override
    public long countByUserId(Long userId) {
        return tasks.values().stream()
                .filter(stored -> userId != null && userId.equals(stored.task().userId()))
                .count();
    }

    @Override
    public Optional<JobMatchTaskResult> findByTaskIdAndUserId(String taskId, Long userId) {
        if (taskId == null || userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tasks.get(taskId))
                .map(StoredTask::task)
                .filter(task -> userId.equals(task.userId()));
    }

    private record StoredTask(JobMatchTaskResult task, String jobDescription, int requestedTopK) {
    }
}
