package com.hewei.hzyjy.xunzhi.career.resume.application;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemoryResumeParseTaskStore implements ResumeParseTaskStore {

    private final ConcurrentMap<String, ResumeParseTaskRecord> tasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Long> displayOrders = new ConcurrentHashMap<>();

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
                .sorted(Comparator.comparing((ResumeParseTaskRecord task) -> displayOrders.getOrDefault(task.resumeId(), Long.MAX_VALUE))
                        .thenComparing(ResumeParseTaskRecord::createTime, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public void updateDisplayOrder(Long userId, List<Long> resumeIds) {
        if (userId == null || resumeIds == null) return;
        for (int index = 0; index < resumeIds.size(); index++) {
            Long resumeId = resumeIds.get(index);
            if (resumeId != null && tasks.values().stream().anyMatch(task -> userId.equals(task.userId()) && resumeId.equals(task.resumeId()))) {
                displayOrders.put(resumeId, (long) index);
            }
        }
    }

    @Override
    public List<ResumeParseTaskRecord> findStaleActiveTasks(Instant updatedBefore, int limit) {
        if (updatedBefore == null || limit <= 0) {
            return List.of();
        }
        return tasks.values().stream()
                .filter(task -> task.status() != null && !task.status().terminal())
                .filter(task -> task.updateTime() != null && task.updateTime().isBefore(updatedBefore))
                .sorted(Comparator.comparing(ResumeParseTaskRecord::updateTime))
                .limit(limit)
                .toList();
    }
}
