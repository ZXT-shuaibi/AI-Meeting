package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeParseTaskDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeParseTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class MySqlResumeParseTaskStore implements ResumeParseTaskStore {

    private final ObjectProvider<CareerResumeParseTaskMapper> mapperProvider;
    private final InMemoryResumeParseTaskStore fallbackStore;
    private static final Set<String> ACTIVE_STATUSES = Set.of(
            ResumeParseTaskStatus.PROCESSING.name(),
            ResumeParseTaskStatus.ANALYZING.name(),
            ResumeParseTaskStatus.SAVING.name()
    );

    @Override
    public ResumeParseTaskRecord save(ResumeParseTaskRecord task) {
        fallbackStore.save(task);
        CareerResumeParseTaskMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return task;
        }
        try {
            CareerResumeParseTaskDO existing = mapper.selectOne(Wrappers.<CareerResumeParseTaskDO>lambdaQuery()
                    .eq(CareerResumeParseTaskDO::getTaskId, task.taskId())
                    .last("limit 1"));
            CareerResumeParseTaskDO row = existing == null ? new CareerResumeParseTaskDO() : existing;
            toRow(task, row);
            if (row.getId() == null) {
                row.setCreateTime(toDate(task.createTime()));
                mapper.insert(row);
            } else {
                mapper.updateById(row);
            }
        } catch (Exception ex) {
            log.warn("MySQL 简历解析任务持久化失败，内存兜底仍可用。任务编号={}", task.taskId(), ex);
        }
        return task;
    }

    @Override
    public Optional<ResumeParseTaskRecord> findByTaskId(String taskId) {
        CareerResumeParseTaskMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                CareerResumeParseTaskDO row = mapper.selectOne(Wrappers.<CareerResumeParseTaskDO>lambdaQuery()
                        .eq(CareerResumeParseTaskDO::getTaskId, taskId)
                        .eq(CareerResumeParseTaskDO::getDelFlag, 0)
                        .last("limit 1"));
                if (row != null) {
                    ResumeParseTaskRecord task = fromRow(row);
                    fallbackStore.save(task);
                    return Optional.of(task);
                }
            } catch (Exception ex) {
            log.warn("MySQL 简历解析任务查询失败，已使用内存兜底。任务编号={}", taskId, ex);
            }
        }
        return fallbackStore.findByTaskId(taskId);
    }

    @Override
    public Optional<ResumeParseTaskRecord> findByTaskIdAndUserId(String taskId, Long userId) {
        if (taskId == null || taskId.isBlank() || userId == null) {
            return Optional.empty();
        }
        CareerResumeParseTaskMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                CareerResumeParseTaskDO row = mapper.selectOne(Wrappers.<CareerResumeParseTaskDO>lambdaQuery()
                        .eq(CareerResumeParseTaskDO::getTaskId, taskId)
                        .eq(CareerResumeParseTaskDO::getUserId, userId)
                        .eq(CareerResumeParseTaskDO::getDelFlag, 0)
                        .last("limit 1"));
                if (row != null) {
                    ResumeParseTaskRecord task = fromRow(row);
                    fallbackStore.save(task);
                    return Optional.of(task);
                }
                return Optional.empty();
            } catch (Exception ex) {
            log.warn("MySQL 简历解析任务归属查询失败，已使用内存兜底。任务编号={}，用户编号={}", taskId, userId, ex);
            }
        }
        return fallbackStore.findByTaskIdAndUserId(taskId, userId);
    }

    @Override
    public List<ResumeParseTaskRecord> findByUserId(Long userId, String status) {
        CareerResumeParseTaskMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null && userId != null) {
            try {
                var query = Wrappers.<CareerResumeParseTaskDO>lambdaQuery()
                        .eq(CareerResumeParseTaskDO::getUserId, userId)
                        .eq(CareerResumeParseTaskDO::getDelFlag, 0);
                if (status != null && !status.isBlank()) {
                    query.eq(CareerResumeParseTaskDO::getStatus, status.toUpperCase());
                }
                List<ResumeParseTaskRecord> tasks = mapper.selectList(query)
                        .stream()
                        .sorted(java.util.Comparator.comparing(CareerResumeParseTaskDO::getDisplayOrder,
                                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                                .thenComparing(CareerResumeParseTaskDO::getCreateTime,
                                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                        .map(this::fromRow)
                        .toList();
                tasks.forEach(fallbackStore::save);
                return tasks;
            } catch (Exception ex) {
            log.warn("MySQL 简历解析任务列表查询失败，已使用内存兜底。用户编号={}", userId, ex);
            }
        }
        return fallbackStore.findByUserId(userId, status);
    }

    @Override
    public void updateDisplayOrder(Long userId, List<Long> resumeIds) {
        CareerResumeParseTaskMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null || userId == null || resumeIds == null) return;
        for (int index = 0; index < resumeIds.size(); index++) {
            Long resumeId = resumeIds.get(index);
            if (resumeId == null) continue;
            mapper.update(null, Wrappers.<CareerResumeParseTaskDO>lambdaUpdate()
                    .eq(CareerResumeParseTaskDO::getUserId, userId)
                    .eq(CareerResumeParseTaskDO::getResumeId, resumeId)
                    .set(CareerResumeParseTaskDO::getDisplayOrder, index));
        }
        fallbackStore.updateDisplayOrder(userId, resumeIds);
    }

    @Override
    public List<ResumeParseTaskRecord> findStaleActiveTasks(Instant updatedBefore, int limit) {
        if (updatedBefore == null || limit <= 0) {
            return List.of();
        }
        CareerResumeParseTaskMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                List<ResumeParseTaskRecord> tasks = mapper.selectList(Wrappers.<CareerResumeParseTaskDO>lambdaQuery()
                                .in(CareerResumeParseTaskDO::getStatus, ACTIVE_STATUSES)
                                .lt(CareerResumeParseTaskDO::getUpdateTime, toDate(updatedBefore))
                                .eq(CareerResumeParseTaskDO::getDelFlag, 0)
                                .orderByAsc(CareerResumeParseTaskDO::getUpdateTime)
                                .last("limit " + Math.max(1, limit)))
                        .stream()
                        .map(this::fromRow)
                        .toList();
                tasks.forEach(fallbackStore::save);
                return tasks;
            } catch (Exception ex) {
            log.warn("MySQL 超时简历解析任务查询失败，已使用内存兜底。", ex);
            }
        }
        return fallbackStore.findStaleActiveTasks(updatedBefore, limit);
    }

    private void toRow(ResumeParseTaskRecord task, CareerResumeParseTaskDO row) {
        row.setTaskId(task.taskId());
        row.setUserId(task.userId());
        row.setStatus(task.status().name());
        row.setResumeId(task.resumeId());
        row.setOriginalFilename(task.originalFilename());
        row.setFileSize(task.fileSize());
        row.setContentType(task.contentType());
        row.setCvType(task.cvType());
        row.setStorageProvider(task.storageProvider());
        row.setStorageKey(task.storageKey());
        row.setFilePath(task.filePath());
        row.setFileSnapshot(task.fileSnapshot());
        row.setRetryOfTaskId(task.retryOfTaskId());
        row.setErrorMessage(task.errorMessage());
        row.setJobDescription(task.jobDescription());
        row.setOptimizationResultJson(task.optimizationResultJson());
        row.setOptimizedAt(toDate(task.optimizedAt()));
        row.setStartTime(toDate(task.startTime()));
        row.setCompleteTime(toDate(task.completeTime()));
        row.setUpdateTime(toDate(task.updateTime()));
        row.setDelFlag(0);
    }

    private ResumeParseTaskRecord fromRow(CareerResumeParseTaskDO row) {
        return new ResumeParseTaskRecord(
                row.getTaskId(),
                row.getUserId(),
                ResumeParseTaskStatus.valueOf(row.getStatus()),
                row.getResumeId(),
                row.getOriginalFilename(),
                row.getFileSize(),
                row.getContentType(),
                row.getCvType(),
                row.getStorageProvider(),
                row.getStorageKey(),
                row.getFilePath(),
                row.getFileSnapshot(),
                row.getRetryOfTaskId(),
                row.getErrorMessage(),
                row.getJobDescription(),
                row.getOptimizationResultJson(),
                toInstant(row.getOptimizedAt()),
                toInstant(row.getStartTime()),
                toInstant(row.getCompleteTime()),
                toInstant(row.getCreateTime()),
                toInstant(row.getUpdateTime())
        );
    }

    private Date toDate(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

    private Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
