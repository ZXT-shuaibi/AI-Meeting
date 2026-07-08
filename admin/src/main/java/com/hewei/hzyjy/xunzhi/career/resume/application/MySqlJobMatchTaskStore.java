package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.alibaba.fastjson2.JSON;
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
            row.setStatus(task.status());
            row.setJobDescription(jobDescription);
            row.setLimitCount(limit);
            row.setMatchedTemplatesJson(JSON.toJSONString(task.matchedTemplates()));
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
            log.warn("MySQL job match task persistence failed, in-memory fallback remains available. taskId={}", task.taskId(), ex);
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
                    List<String> templates = JSON.parseArray(row.getMatchedTemplatesJson(), String.class);
                    JobMatchTaskResult result = new JobMatchTaskResult(
                            row.getTaskId(),
                            row.getStatus(),
                            templates == null ? List.of() : templates,
                            row.getErrorMessage()
                    );
                    fallbackStore.save(result, row.getJobDescription(), row.getLimitCount() == null ? 0 : row.getLimitCount(), row.getErrorMessage());
                    return Optional.of(result);
                }
            } catch (Exception ex) {
                log.warn("MySQL job match task lookup failed, using in-memory fallback. taskId={}", taskId, ex);
            }
        }
        return fallbackStore.findByTaskId(taskId);
    }
}
