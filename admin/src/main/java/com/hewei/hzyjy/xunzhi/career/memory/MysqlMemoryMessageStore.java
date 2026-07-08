package com.hewei.hzyjy.xunzhi.career.memory;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.memory.dao.entity.CareerMemoryMessageDO;
import com.hewei.hzyjy.xunzhi.career.memory.dao.mapper.CareerMemoryMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MysqlMemoryMessageStore {

    private final ObjectProvider<CareerMemoryMessageMapper> mapperProvider;

    public void replace(String memoryId, List<MemoryMessage> messages) {
        CareerMemoryMessageMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null || memoryId == null) {
            return;
        }
        try {
            mapper.delete(Wrappers.<CareerMemoryMessageDO>lambdaQuery().eq(CareerMemoryMessageDO::getMemoryId, memoryId));
            Date now = new Date();
            for (int i = 0; i < messages.size(); i++) {
                mapper.insert(toRow(memoryId, i, messages.get(i), now));
            }
        } catch (Exception ex) {
            log.warn("MySQL memory message persistence failed. memoryId={}", memoryId, ex);
        }
    }

    public List<MemoryMessage> load(String memoryId) {
        CareerMemoryMessageMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null || memoryId == null) {
            return List.of();
        }
        try {
            return mapper.selectList(Wrappers.<CareerMemoryMessageDO>lambdaQuery()
                            .eq(CareerMemoryMessageDO::getMemoryId, memoryId)
                            .eq(CareerMemoryMessageDO::getDelFlag, 0)
                            .orderByAsc(CareerMemoryMessageDO::getMessageIndex))
                    .stream()
                    .map(this::fromRow)
                    .toList();
        } catch (Exception ex) {
            log.warn("MySQL memory message restore failed. memoryId={}", memoryId, ex);
            return List.of();
        }
    }

    private CareerMemoryMessageDO toRow(String memoryId, int index, MemoryMessage message, Date now) {
        CareerMemoryMessageDO row = new CareerMemoryMessageDO();
        row.setMemoryId(memoryId);
        row.setMessageIndex(index);
        row.setRole(message.role() == null ? MemoryRole.SYSTEM.name() : message.role().name());
        row.setContent(message.content() == null ? "" : message.content());
        row.setMessageTime(message.timestamp() == null ? Instant.now() : message.timestamp());
        row.setMetadataJson(JSON.toJSONString(message.metadata() == null ? Map.of() : message.metadata()));
        row.setCreateTime(now);
        row.setUpdateTime(now);
        row.setDelFlag(0);
        return row;
    }

    @SuppressWarnings("unchecked")
    private MemoryMessage fromRow(CareerMemoryMessageDO row) {
        return MemoryMessage.builder()
                .role(parseRole(row.getRole()))
                .content(row.getContent())
                .timestamp(row.getMessageTime())
                .metadata(row.getMetadataJson() == null ? Map.of() : JSON.parseObject(row.getMetadataJson(), Map.class))
                .build();
    }

    private MemoryRole parseRole(String value) {
        try {
            return value == null || value.isBlank() ? MemoryRole.SYSTEM : MemoryRole.valueOf(value);
        } catch (Exception ex) {
            return MemoryRole.SYSTEM;
        }
    }
}