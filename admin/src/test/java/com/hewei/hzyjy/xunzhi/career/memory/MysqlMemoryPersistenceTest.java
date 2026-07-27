package com.hewei.hzyjy.xunzhi.career.memory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.hewei.hzyjy.xunzhi.career.memory.dao.entity.CareerMemoryDecisionDO;
import com.hewei.hzyjy.xunzhi.career.memory.dao.entity.CareerMemoryMessageDO;
import com.hewei.hzyjy.xunzhi.career.memory.dao.mapper.CareerMemoryDecisionMapper;
import com.hewei.hzyjy.xunzhi.career.memory.dao.mapper.CareerMemoryMessageMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlMemoryPersistenceTest {

    @Test
    void evictsInactiveMessageHistoryFromTheHotCacheAndRestoresItFromColdStorage() {
        CareerMemoryMessageMapper messageMapper = mock(CareerMemoryMessageMapper.class);
        MysqlMemoryMessageStore coldStore = new MysqlMemoryMessageStore(provider(messageMapper));
        AtomicLong tickerNanos = new AtomicLong();
        HybridCompactingChatMemory memory = new HybridCompactingChatMemory(
                request -> null,
                new InterviewRuleBasedScorer(),
                new DecisionIndex(),
                null,
                coldStore,
                Caffeine.<String, List<MemoryMessage>>newBuilder()
                        .maximumSize(1)
                        .expireAfterAccess(Duration.ofMinutes(30))
                        .ticker(tickerNanos::get)
                        .build()
        );
        memory.add("inactive-session", MemoryMessage.builder().role(MemoryRole.USER).content("Java").build());
        ArgumentCaptor<CareerMemoryMessageDO> saved = ArgumentCaptor.forClass(CareerMemoryMessageDO.class);
        verify(messageMapper).insert(saved.capture());
        when(messageMapper.selectList(any())).thenReturn(List.of(saved.getValue()));
        // add 阶段会预热热缓存并读取一次冷存储；只校验过期后的恢复行为。
        clearInvocations(messageMapper);

        tickerNanos.addAndGet(Duration.ofMinutes(31).toNanos());

        assertEquals("Java", memory.messages("inactive-session").get(0).content());
        verify(messageMapper).selectList(any());
    }

    @Test
    void hybridMemoryPersistsAndRestoresMessagesThroughColdStore() {
        CareerMemoryMessageMapper messageMapper = mock(CareerMemoryMessageMapper.class);
        MysqlMemoryMessageStore coldStore = new MysqlMemoryMessageStore(provider(messageMapper));
        DecisionIndex decisionIndex = new DecisionIndex();
        HybridCompactingChatMemory memory = new HybridCompactingChatMemory(request -> null, new InterviewRuleBasedScorer(), decisionIndex, null, coldStore);

        memory.add("session-1", MemoryMessage.builder()
                .role(MemoryRole.USER)
                .content("Need Java backend JD alignment")
                .metadata(Map.of("scene", "JD_ALIGNMENT"))
                .build());

        ArgumentCaptor<CareerMemoryMessageDO> saved = ArgumentCaptor.forClass(CareerMemoryMessageDO.class);
        verify(messageMapper).delete(any());
        verify(messageMapper).insert(saved.capture());
        assertEquals("session-1", saved.getValue().getMemoryId());
        assertEquals("USER", saved.getValue().getRole());
        assertEquals("Need Java backend JD alignment", saved.getValue().getContent());
        assertTrue(saved.getValue().getMetadataJson().contains("JD_ALIGNMENT"));

        when(messageMapper.selectList(any())).thenReturn(List.of(saved.getValue()));
        HybridCompactingChatMemory restored = new HybridCompactingChatMemory(request -> null, new InterviewRuleBasedScorer(), decisionIndex, null, coldStore);

        List<MemoryMessage> messages = restored.messages("session-1");

        assertEquals(1, messages.size());
        assertEquals(MemoryRole.USER, messages.get(0).role());
        assertEquals("Need Java backend JD alignment", messages.get(0).content());
    }

    @Test
    void decisionIndexPersistsAndRestoresThroughColdStore() {
        CareerMemoryDecisionMapper decisionMapper = mock(CareerMemoryDecisionMapper.class);
        MysqlDecisionStore coldStore = new MysqlDecisionStore(provider(decisionMapper));
        DecisionIndex index = new DecisionIndex(null, coldStore);

        index.record("session-1", 3, "Reflect decision NEXT");

        ArgumentCaptor<CareerMemoryDecisionDO> saved = ArgumentCaptor.forClass(CareerMemoryDecisionDO.class);
        verify(decisionMapper).delete(any());
        verify(decisionMapper).insert(saved.capture());
        assertEquals("session-1", saved.getValue().getMemoryId());
        assertEquals(3, saved.getValue().getMessageIndex());
        assertEquals("Reflect decision NEXT", saved.getValue().getSummary());

        when(decisionMapper.selectList(any())).thenReturn(List.of(saved.getValue()));
        DecisionIndex restored = new DecisionIndex(null, coldStore);

        List<DecisionEntry> decisions = restored.getDecisions("session-1");

        assertEquals(1, decisions.size());
        assertEquals("Reflect decision NEXT", decisions.get(0).summary());
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }
}
