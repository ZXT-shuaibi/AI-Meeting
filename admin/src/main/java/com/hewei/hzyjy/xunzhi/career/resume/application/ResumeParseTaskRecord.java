package com.hewei.hzyjy.xunzhi.career.resume.application;

import java.time.Instant;

public record ResumeParseTaskRecord(
        String taskId,
        Long userId,
        ResumeParseTaskStatus status,
        Long resumeId,
        String originalFilename,
        Long fileSize,
        String contentType,
        String cvType,
        String storageProvider,
        String storageKey,
        String filePath,
        byte[] fileSnapshot,
        String retryOfTaskId,
        String errorMessage,
        Instant startTime,
        Instant completeTime,
        Instant createTime,
        Instant updateTime
) {
    public ResumeParseTaskRecord {
        fileSnapshot = fileSnapshot == null ? new byte[0] : fileSnapshot.clone();
    }

    @Override
    public byte[] fileSnapshot() {
        return fileSnapshot.clone();
    }

    public ResumeParseTaskRecord withStatus(ResumeParseTaskStatus next) {
        Instant now = Instant.now();
        return new ResumeParseTaskRecord(taskId, userId, next, resumeId, originalFilename, fileSize, contentType, cvType,
                storageProvider, storageKey, filePath, fileSnapshot, retryOfTaskId, errorMessage,
                startTime, next.terminal() ? now : completeTime, createTime, now);
    }

    public ResumeParseTaskRecord completed(Long completedResumeId) {
        Instant now = Instant.now();
        return new ResumeParseTaskRecord(taskId, userId, ResumeParseTaskStatus.COMPLETED, completedResumeId, originalFilename,
                fileSize, contentType, cvType, storageProvider, storageKey, filePath, fileSnapshot, retryOfTaskId, null,
                startTime, now, createTime, now);
    }

    public ResumeParseTaskRecord failed(String message) {
        Instant now = Instant.now();
        return new ResumeParseTaskRecord(taskId, userId, ResumeParseTaskStatus.FAILED, resumeId, originalFilename,
                fileSize, contentType, cvType, storageProvider, storageKey, filePath, fileSnapshot, retryOfTaskId, message,
                startTime, now, createTime, now);
    }

    public ResumeParseTaskRecord retry(String newTaskId) {
        Instant now = Instant.now();
        return new ResumeParseTaskRecord(newTaskId, userId, ResumeParseTaskStatus.PROCESSING, null, originalFilename,
                fileSize, contentType, cvType, storageProvider, storageKey, filePath, fileSnapshot, taskId, null,
                now, null, now, now);
    }
}
