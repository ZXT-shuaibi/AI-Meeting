package com.hewei.hzyjy.xunzhi.career.observability;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hewei.hzyjy.xunzhi.career.config.CareerObservabilityProperties;
import com.hewei.hzyjy.xunzhi.career.observability.dao.entity.AiInvocationTraceDO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiTraceEventListenerTest {

    @Test
    void completedUpdatePersistsDegradedMetadataToColdTrace() {
        AiTraceEventListener listener = new AiTraceEventListener(
                new CareerObservabilityProperties(),
                provider((StringRedisTemplate) null),
                provider(null),
                provider(null),
                provider(null)
        );
        Instant start = Instant.parse("2026-07-08T10:00:00Z");
        AiInvocationCompletedEvent event = new AiInvocationCompletedEvent(
                "trace-1",
                "session-1",
                null,
                null,
                "RESUME_REVIEW",
                "RESUME_REVIEW",
                "RESUME_REVIEW",
                "spring-ai",
                null,
                start,
                start.plusMillis(12),
                12L,
                "input",
                "fallback output",
                null,
                false,
                Map.of("degraded", true, "fallbackAfterFailure", true)
        );
        AiInvocationTraceDO trace = AiInvocationTraceDO.fromCompleted(event);

        UpdateWrapper<AiInvocationTraceDO> wrapper = listener.completedUpdateWrapper(trace);

        String sqlSet = String.valueOf(wrapper.getSqlSet());
        assertTrue(sqlSet.contains("metadata_json"));
        assertTrue(trace.getMetadataJson().contains("degraded"));
        assertTrue(trace.getMetadataJson().contains("fallbackAfterFailure"));
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