package com.hewei.hzyjy.xunzhi.career.resume.application;

import java.util.Optional;

public interface JobMatchTaskStore {

    JobMatchTaskResult save(JobMatchTaskResult task, String jobDescription, int limit, String errorMessage);

    Optional<JobMatchTaskResult> findByTaskId(String taskId);
}
