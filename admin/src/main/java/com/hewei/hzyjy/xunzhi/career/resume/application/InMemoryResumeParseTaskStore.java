package com.hewei.hzyjy.xunzhi.career.resume.application;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemoryResumeParseTaskStore implements ResumeParseTaskStore {

    private final ConcurrentMap<String, ResumeParseTaskRecord> tasks = new ConcurrentHashMap<>();

    @Override
    public ResumeParseTaskRecord save(ResumeParseTaskRecord task) {
        tasks.put(task.taskId(), task);
        return task;
    }

    @Override
    public Optional<ResumeParseTaskRecord> findByTaskId(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public Optional<ResumeParseTaskRecord> findByTaskIdAndUserId(String taskId, Long userId) {
        return findByTaskId(taskId)
                .filter(task -> userId != null && userId.equals(task.userId()));
    }

    @Override
    public List<ResumeParseTaskRecord> findByUserId(Long userId, String status) {
        return tasks.values().stream()
                .filter(task -> userId != null && userId.equals(task.userId()))
                .filter(task -> status == null || status.isBlank() || task.status().name().equalsIgnoreCase(status))
                .sorted(Comparator.comparing(ResumeParseTaskRecord::createTime).reversed())
                .toList();
    }
}
