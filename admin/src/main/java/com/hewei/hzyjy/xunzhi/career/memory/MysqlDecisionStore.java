package com.hewei.hzyjy.xunzhi.career.memory;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.memory.dao.entity.CareerMemoryDecisionDO;
import com.hewei.hzyjy.xunzhi.career.memory.dao.mapper.CareerMemoryDecisionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MysqlDecisionStore {

    private final ObjectProvider<CareerMemoryDecisionMapper> mapperProvider;

    public void replace(Object memoryId, List<DecisionEntry> decisions) {
        CareerMemoryDecisionMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null || memoryId == null) {
            return;
        }
        String key = String.valueOf(memoryId);
        try {
            mapper.delete(Wrappers.<CareerMemoryDecisionDO>lambdaQuery().eq(CareerMemoryDecisionDO::getMemoryId, key));
            Date now = new Date();
            for (DecisionEntry decision : decisions) {
                mapper.insert(toRow(key, decision, now));
            }
        } catch (Exception ex) {
            log.warn("MySQL decision persistence failed. memoryId={}", memoryId, ex);
        }
    }

    public List<DecisionEntry> load(Object memoryId) {
        CareerMemoryDecisionMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null || memoryId == null) {
            return List.of();
        }
        String key = String.valueOf(memoryId);
        try {
            return mapper.selectList(Wrappers.<CareerMemoryDecisionDO>lambdaQuery()
                            .eq(CareerMemoryDecisionDO::getMemoryId, key)
                            .eq(CareerMemoryDecisionDO::getDelFlag, 0)
                            .orderByAsc(CareerMemoryDecisionDO::getMessageIndex)
                            .orderByAsc(CareerMemoryDecisionDO::getId))
                    .stream()
                    .map(row -> new DecisionEntry(
                            row.getMessageIndex() == null ? 0 : row.getMessageIndex(),
                            row.getSummary(),
                            row.getDecisionTime() == null ? Instant.now() : row.getDecisionTime()))
                    .toList();
        } catch (Exception ex) {
            log.warn("MySQL decision restore failed. memoryId={}", memoryId, ex);
            return List.of();
        }
    }

    private CareerMemoryDecisionDO toRow(String memoryId, DecisionEntry decision, Date now) {
        CareerMemoryDecisionDO row = new CareerMemoryDecisionDO();
        row.setMemoryId(memoryId);
        row.setMessageIndex(decision.messageIndex());
        row.setSummary(decision.summary() == null ? "" : decision.summary());
        row.setDecisionTime(decision.timestamp() == null ? Instant.now() : decision.timestamp());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        row.setDelFlag(0);
        return row;
    }
}