package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerJobMatchTaskDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerJobMatchTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class MySqlJobMatchTaskStore implements JobMatchTaskStore {

    private final ObjectProvider<CareerJobMatchTaskMapper> mapperProvider;
    private final InMemoryJobMatchTaskStore fallbackStore;

    @Override
    public JobMatchTaskResult save(JobMatchTaskResult task, String jobDescription, int limit, String errorMessage) {
        fallbackStore.save(task, jobDescription, limit, errorMessage);
        CareerJobMatchTaskMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return task;
        }
        try {
            CareerJobMatchTaskDO existing = mapper.selectOne(Wrappers.<CareerJobMatchTaskDO>lambdaQuery()
                    .eq(CareerJobMatchTaskDO::getTaskId, task.taskId())
                    .last("limit 1"));
            CareerJobMatchTaskDO row = existing == null ? new CareerJobMatchTaskDO() : existing;
            row.setTaskId(task.taskId());
            row.setUserId(task.userId());
            row.setStatus(task.status());
            row.setJobDescription(jobDescription);
            row.setLimitCount(limit);
            row.setMatchedTemplatesJson(JSON.toJSONString(new JobMatchPayload(task.selectedResumeIds(), task.matchedResumes(), task.matchedTemplates())));
            row.setErrorMessage(errorMessage);
            row.setUpdateTime(new Date());
            row.setDelFlag(0);
            if (row.getId() == null) {
                row.setCreateTime(new Date());
                mapper.insert(row);
            } else {
                mapper.updateById(row);
            }
        } catch (Exception ex) {
            log.warn("MySQL 岗位匹配任务持久化失败，内存兜底仍可用。任务编号={}", task.taskId(), ex);
        }
        return task;
    }

    @Override
    public Optional<JobMatchTaskResult> findByTaskId(String taskId) {
        CareerJobMatchTaskMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                CareerJobMatchTaskDO row = mapper.selectOne(Wrappers.<CareerJobMatchTaskDO>lambdaQuery()
                        .eq(CareerJobMatchTaskDO::getTaskId, taskId)
                        .eq(CareerJobMatchTaskDO::getDelFlag, 0)
                        .last("limit 1"));
                if (row != null) {
                    JobMatchTaskResult result = fromRow(row);
                    fallbackStore.save(result, row.getJobDescription(), row.getLimitCount() == null ? 0 : row.getLimitCount(), row.getErrorMessage());
                    return Optional.of(result);
                }
            } catch (Exception ex) {
            log.warn("MySQL 岗位匹配任务查询失败，已使用内存兜底。任务编号={}", taskId, ex);
            }
        }
        return fallbackStore.findByTaskId(taskId);
    }

    @Override
    public Optional<JobMatchTaskResult> findByTaskIdAndUserId(String taskId, Long userId) {
        if (taskId == null || taskId.isBlank() || userId == null) {
            return Optional.empty();
        }
        CareerJobMatchTaskMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                CareerJobMatchTaskDO row = mapper.selectOne(Wrappers.<CareerJobMatchTaskDO>lambdaQuery()
                        .eq(CareerJobMatchTaskDO::getTaskId, taskId)
                        .eq(CareerJobMatchTaskDO::getUserId, userId)
                        .eq(CareerJobMatchTaskDO::getDelFlag, 0)
                        .last("limit 1"));
                if (row != null) {
                    JobMatchTaskResult result = fromRow(row);
                    fallbackStore.save(result, row.getJobDescription(), row.getLimitCount() == null ? 0 : row.getLimitCount(), row.getErrorMessage());
                    return Optional.of(result);
                }
                return Optional.empty();
            } catch (Exception ex) {
            log.warn("MySQL 岗位匹配任务归属查询失败，已使用内存兜底。任务编号={}，用户编号={}", taskId, userId, ex);
            }
        }
        return fallbackStore.findByTaskIdAndUserId(taskId, userId);
    }

    @Override
    public List<JobMatchHistoryItem> findRecentByUserId(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }
        CareerJobMatchTaskMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return fallbackStore.findRecentByUserId(userId, limit);
        }
        try {
            var query = Wrappers.<CareerJobMatchTaskDO>lambdaQuery()
                            .eq(CareerJobMatchTaskDO::getUserId, userId)
                            .eq(CareerJobMatchTaskDO::getDelFlag, 0)
                            .orderByDesc(CareerJobMatchTaskDO::getCreateTime);
            if (limit > 0) query.last("limit " + limit);
            return mapper.selectList(query)
                    .stream()
                    .map(row -> {
                        JobMatchTaskResult task = fromRow(row);
                        return new JobMatchHistoryItem(task.taskId(), task.status(), row.getJobDescription(),
                                task.selectedResumeIds().size(), task.selectedResumeIds(),
                                row.getLimitCount() == null ? task.matchedResumes().size() : row.getLimitCount(),
                                task.matchedResumes(), task.errorMessage(),
                                row.getCreateTime() == null ? null : row.getCreateTime().toInstant());
                    })
                    .toList();
        } catch (Exception ex) {
            log.warn("MySQL 岗位匹配历史查询失败，已使用内存兜底。用户编号={}", userId, ex);
            return fallbackStore.findRecentByUserId(userId, limit);
        }
    }

    @Override
    public long countByUserId(Long userId) {
        CareerJobMatchTaskMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null || userId == null) return fallbackStore.countByUserId(userId);
        try {
            return mapper.selectCount(Wrappers.<CareerJobMatchTaskDO>lambdaQuery()
                    .eq(CareerJobMatchTaskDO::getUserId, userId)
                    .eq(CareerJobMatchTaskDO::getDelFlag, 0));
        } catch (Exception ex) {
            log.warn("MySQL 岗位匹配任务数量查询失败，已使用内存兜底。用户编号={}", userId, ex);
            return fallbackStore.countByUserId(userId);
        }
    }

    private JobMatchTaskResult fromRow(CareerJobMatchTaskDO row) {
        String json = row.getMatchedTemplatesJson();
        if (json == null || json.isBlank()) {
            return new JobMatchTaskResult(row.getTaskId(), row.getUserId(), row.getStatus(), List.of(), List.of(), List.of(), row.getErrorMessage());
        }
        if (json.trim().startsWith("[")) {
            List<String> templates = JSON.parseArray(json, String.class);
            return new JobMatchTaskResult(row.getTaskId(), row.getUserId(), row.getStatus(),
                    templates == null ? List.of() : templates, List.of(), List.of(), row.getErrorMessage());
        }
        JSONObject payload = JSON.parseObject(json);
        List<Long> selectedResumeIds = payload.getList("selectedResumeIds", Long.class);
        List<JobMatchCandidate> candidates = payload.getList("matchedResumes", JobMatchCandidate.class);
        List<String> templates = payload.getList("matchedTemplates", String.class);
        return new JobMatchTaskResult(
                row.getTaskId(),
                row.getUserId(),
                row.getStatus(),
                templates == null ? List.of() : templates,
                selectedResumeIds == null ? List.of() : selectedResumeIds,
                candidates == null ? List.of() : candidates,
                row.getErrorMessage()
        );
    }

    private record JobMatchPayload(
            List<Long> selectedResumeIds,
            List<JobMatchCandidate> matchedResumes,
            List<String> matchedTemplates
    ) { }
}
