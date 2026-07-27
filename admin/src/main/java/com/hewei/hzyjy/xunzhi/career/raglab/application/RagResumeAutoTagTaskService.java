package com.hewei.hzyjy.xunzhi.career.raglab.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagResumeAutoTagTaskDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagResumeAutoTagTaskMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.model.RagResumeAutoTagTaskResult;
import com.hewei.hzyjy.xunzhi.career.raglab.model.RagResumeAutoTagTaskStatus;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 将耗时且不可预测的模型标签提取转换为可查询的异步任务。
 *
 * <p>HTTP 请求只负责提交任务；同一用户、同一简历在执行中只允许存在一条 activeKey，重复点击会复用任务而非重复调用模型。</p>
 */
@Slf4j
@Service
public class RagResumeAutoTagTaskService {

    private final RagResumeAutoTagService autoTagService;
    private final RagResumeAutoTagTaskMapper taskMapper;
    private final TaskExecutor taskExecutor;
    private final ConcurrentMap<String, Object> localSubmitLocks = new ConcurrentHashMap<>();

    public RagResumeAutoTagTaskService(RagResumeAutoTagService autoTagService,
                                       RagResumeAutoTagTaskMapper taskMapper,
                                       @Qualifier("careerTaskExecutor") TaskExecutor taskExecutor) {
        this.autoTagService = autoTagService;
        this.taskMapper = taskMapper;
        this.taskExecutor = taskExecutor;
    }

    public RagResumeAutoTagTaskResult submit(Long userId, Long resumeId, CvBO cv) {
        if (userId == null || resumeId == null || cv == null) {
            throw new ClientException("简历预评任务参数不完整");
        }
        String activeKey = activeKey(userId, resumeId);
        Object lock = localSubmitLocks.computeIfAbsent(activeKey, ignored -> new Object());
        synchronized (lock) {
            try {
                RagResumeAutoTagTaskDO existing = findActive(activeKey);
                if (existing != null) {
                    return result(existing, true);
                }
                RagResumeAutoTagTaskDO task = new RagResumeAutoTagTaskDO();
                task.setTaskId(UUID.randomUUID().toString());
                task.setOwnerUserId(userId);
                task.setResumeId(resumeId);
                task.setActiveKey(activeKey);
                task.setStatus(RagResumeAutoTagTaskStatus.PROCESSING.name());
                task.setTagCount(0);
                task.setStartedAt(new Date());
                task.setDelFlag(0);
                try {
                    taskMapper.insert(task);
                } catch (DuplicateKeyException duplicate) {
                    RagResumeAutoTagTaskDO running = findActive(activeKey);
                    if (running != null) {
                        return result(running, true);
                    }
                    throw duplicate;
                }
                try {
                    taskExecutor.execute(() -> execute(task, cv));
                } catch (RuntimeException ex) {
                    markFailed(task, "任务执行队列暂时不可用，请稍后重试", ex);
                    throw new ClientException("简历预评任务提交失败，请稍后重试");
                }
                log.info("AI 简历预评任务已提交，taskId={}, resumeId={}, userId={}", task.getTaskId(), resumeId, userId);
                return result(task, false);
            } finally {
                localSubmitLocks.remove(activeKey, lock);
            }
        }
    }

    public RagResumeAutoTagTaskResult query(Long userId, Long resumeId, String taskId) {
        RagResumeAutoTagTaskDO task = taskMapper.selectOne(Wrappers.<RagResumeAutoTagTaskDO>lambdaQuery()
                .eq(RagResumeAutoTagTaskDO::getTaskId, taskId)
                .eq(RagResumeAutoTagTaskDO::getOwnerUserId, userId)
                .eq(RagResumeAutoTagTaskDO::getResumeId, resumeId)
                .eq(RagResumeAutoTagTaskDO::getDelFlag, 0)
                .last("limit 1"));
        if (task == null) {
            throw new ClientException("简历预评任务不存在或无权访问");
        }
        return result(task, false);
    }

    private void execute(RagResumeAutoTagTaskDO task, CvBO cv) {
        Instant startedAt = Instant.now();
        log.info("AI 简历预评任务开始执行，taskId={}, resumeId={}", task.getTaskId(), task.getResumeId());
        try {
            int tagCount = autoTagService.evaluateOnDemand(cv).size();
            task.setStatus(RagResumeAutoTagTaskStatus.COMPLETED.name());
            task.setTagCount(tagCount);
            task.setErrorMessage(null);
            task.setActiveKey(null);
            task.setCompletedAt(new Date());
            taskMapper.updateById(task);
            log.info("AI 简历预评任务完成，taskId={}, resumeId={}, tagCount={}, 总耗时毫秒={}",
                    task.getTaskId(), task.getResumeId(), tagCount, Duration.between(startedAt, Instant.now()).toMillis());
        } catch (Exception ex) {
            markFailed(task, messageOf(ex), ex);
        }
    }

    private void markFailed(RagResumeAutoTagTaskDO task, String message, Exception cause) {
        task.setStatus(RagResumeAutoTagTaskStatus.FAILED.name());
        task.setErrorMessage(message);
        task.setActiveKey(null);
        task.setCompletedAt(new Date());
        try {
            taskMapper.updateById(task);
        } catch (Exception persistenceError) {
            log.error("AI 简历预评任务失败状态写入异常，taskId={}", task.getTaskId(), persistenceError);
        }
        log.warn("AI 简历预评任务失败，taskId={}, resumeId={}, 原因={}", task.getTaskId(), task.getResumeId(), message, cause);
    }

    private RagResumeAutoTagTaskDO findActive(String activeKey) {
        return taskMapper.selectOne(Wrappers.<RagResumeAutoTagTaskDO>lambdaQuery()
                .eq(RagResumeAutoTagTaskDO::getActiveKey, activeKey)
                .eq(RagResumeAutoTagTaskDO::getStatus, RagResumeAutoTagTaskStatus.PROCESSING.name())
                .eq(RagResumeAutoTagTaskDO::getDelFlag, 0)
                .last("limit 1"));
    }

    private RagResumeAutoTagTaskResult result(RagResumeAutoTagTaskDO task, boolean reused) {
        return new RagResumeAutoTagTaskResult(task.getTaskId(), RagResumeAutoTagTaskStatus.valueOf(task.getStatus()),
                task.getTagCount() == null ? 0 : task.getTagCount(), task.getErrorMessage(), reused);
    }

    private String activeKey(Long userId, Long resumeId) {
        return "resume-tag:" + userId + ':' + resumeId;
    }

    private String messageOf(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "AI 简历预评失败，请稍后重试" : message.substring(0, Math.min(500, message.length()));
    }
}
