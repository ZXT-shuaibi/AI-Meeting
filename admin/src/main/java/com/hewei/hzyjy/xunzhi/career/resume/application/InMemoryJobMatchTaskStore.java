package com.hewei.hzyjy.xunzhi.career.resume.application;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryJobMatchTaskStore implements JobMatchTaskStore {

    private final Map<String, JobMatchTaskResult> tasks = new ConcurrentHashMap<>();

    @Override
    public JobMatchTaskResult save(JobMatchTaskResult task, String jobDescription, int limit, String errorMessage) {
        tasks.put(task.taskId(), task);
        return task;
    }

    @Override
    public Optional<JobMatchTaskResult> findByTaskId(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }
}
