package com.hewei.hzyjy.xunzhi.career.observability;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.config.CareerObservabilityProperties;
import com.hewei.hzyjy.xunzhi.career.observability.dao.entity.AiAgentSessionStatsDO;
import com.hewei.hzyjy.xunzhi.career.observability.dao.entity.AiInvocationTraceDO;
import com.hewei.hzyjy.xunzhi.career.observability.dao.entity.AiToolExecutionDO;
import com.hewei.hzyjy.xunzhi.career.observability.dao.mapper.AiAgentSessionStatsMapper;
import com.hewei.hzyjy.xunzhi.career.observability.dao.mapper.AiInvocationTraceMapper;
import com.hewei.hzyjy.xunzhi.career.observability.dao.mapper.AiToolExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiTraceEventListener {

    private static final String TRACE_KEY_PREFIX = "xunzhi-agent:observability:trace:";
    private static final String SESSION_TRACE_KEY_PREFIX = "xunzhi-agent:observability:session:";

    private final CareerObservabilityProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<AiInvocationTraceMapper> traceMapperProvider;
    private final ObjectProvider<AiToolExecutionMapper> toolMapperProvider;
    private final ObjectProvider<AiAgentSessionStatsMapper> statsMapperProvider;

    @Async
    @EventListener
    public void onStarted(AiInvocationStartedEvent event) {
        if (!properties.enabledFor(event.sceneCode())) {
            return;
        }
        writeHotTrace(event.traceId(), event.sessionId(), event);
        insertStarted(AiInvocationTraceDO.fromStarted(event));
    }

    @Async
    @EventListener
    public void onCompleted(AiInvocationCompletedEvent event) {
        if (!properties.enabledFor(event.sceneCode())) {
            return;
        }
        writeHotTrace(event.traceId(), event.sessionId(), event);
        AiInvocationTraceDO trace = AiInvocationTraceDO.fromCompleted(event);
        updateCompleted(trace);
        updateStats(trace, true);
    }

    @Async
    @EventListener
    public void onFailed(AiInvocationFailedEvent event) {
        if (!properties.enabledFor(event.sceneCode())) {
            return;
        }
        writeHotTrace(event.traceId(), event.sessionId(), event);
        AiInvocationTraceDO trace = AiInvocationTraceDO.fromFailed(event);
        updateCompleted(trace);
        updateStats(trace, false);
    }

    @Async
    @EventListener
    public void onToolExecution(AiToolExecutionEvent event) {
        if (!properties.enabledFor(event.sceneCode())) {
            return;
        }
        writeHotTrace(event.traceId(), event.sessionId(), event);
        AiToolExecutionMapper mapper = toolMapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                mapper.insert(AiToolExecutionDO.from(event));
            } catch (Exception ex) {
                log.debug("AI tool cold trace insert failed", ex);
            }
        }
    }

    private void writeHotTrace(String traceId, String sessionId, Object event) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || traceId == null) {
            return;
        }
        try {
            Duration ttl = Duration.ofSeconds(properties.getRedisTtlSeconds());
            redisTemplate.opsForValue().set(TRACE_KEY_PREFIX + traceId, JSON.toJSONString(event), ttl);
            if (sessionId != null && !sessionId.isBlank()) {
                String key = SESSION_TRACE_KEY_PREFIX + sessionId + ":traces";
                redisTemplate.opsForList().leftPush(key, traceId);
                redisTemplate.expire(key, ttl);
            }
        } catch (Exception ex) {
            log.debug("AI hot trace write failed. traceId={}", traceId, ex);
        }
    }

    private void insertStarted(AiInvocationTraceDO trace) {
        AiInvocationTraceMapper mapper = traceMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        try {
            Date now = new Date();
            trace.setCreateTime(now);
            trace.setUpdateTime(now);
            trace.setDelFlag(0);
            mapper.insert(trace);
        } catch (Exception ex) {
            log.debug("AI invocation cold trace start insert failed. traceId={}", trace.getTraceId(), ex);
        }
    }

    private void updateCompleted(AiInvocationTraceDO trace) {
        AiInvocationTraceMapper mapper = traceMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        try {
            LambdaUpdateWrapper<AiInvocationTraceDO> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(AiInvocationTraceDO::getTraceId, trace.getTraceId())
                    .set(AiInvocationTraceDO::getEventType, trace.getEventType())
                    .set(AiInvocationTraceDO::getEndTime, trace.getEndTime())
                    .set(AiInvocationTraceDO::getDurationMs, trace.getDurationMs())
                    .set(AiInvocationTraceDO::getOutputSummary, trace.getOutputSummary())
                    .set(AiInvocationTraceDO::getErrorMessage, trace.getErrorMessage())
                    .set(AiInvocationTraceDO::getErrorStackTrace, trace.getErrorStackTrace())
                    .set(AiInvocationTraceDO::getUpdateTime, new Date());
            int updated = mapper.update(null, wrapper);
            if (updated == 0) {
                Date now = new Date();
                trace.setCreateTime(now);
                trace.setUpdateTime(now);
                trace.setDelFlag(0);
                mapper.insert(trace);
            }
        } catch (Exception ex) {
            log.debug("AI invocation cold trace update failed. traceId={}", trace.getTraceId(), ex);
        }
    }

    private void updateStats(AiInvocationTraceDO trace, boolean success) {
        if (trace.getSessionId() == null || trace.getSessionId().isBlank()) {
            return;
        }
        AiAgentSessionStatsMapper mapper = statsMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        try {
            AiAgentSessionStatsDO stats = mapper.selectOne(Wrappers.<AiAgentSessionStatsDO>lambdaQuery()
                    .eq(AiAgentSessionStatsDO::getSessionId, trace.getSessionId())
                    .eq(AiAgentSessionStatsDO::getSceneCode, trace.getSceneCode())
                    .last("limit 1"));
            Date now = new Date();
            if (stats == null) {
                stats = new AiAgentSessionStatsDO();
                stats.setSessionId(trace.getSessionId());
                stats.setSceneCode(trace.getSceneCode());
                stats.setSuccessCount(0L);
                stats.setFailureCount(0L);
                stats.setTotalDurationMs(0L);
                stats.setCreateTime(now);
                stats.setDelFlag(0);
            }
            stats.setSuccessCount(stats.getSuccessCount() + (success ? 1 : 0));
            stats.setFailureCount(stats.getFailureCount() + (success ? 0 : 1));
            stats.setTotalDurationMs(stats.getTotalDurationMs() + Math.max(0L, trace.getDurationMs() == null ? 0L : trace.getDurationMs()));
            stats.setLastTraceId(trace.getTraceId());
            stats.setUpdateTime(now);
            if (stats.getId() == null) {
                mapper.insert(stats);
            } else {
                mapper.updateById(stats);
            }
        } catch (Exception ex) {
            log.debug("AI session stats update failed. traceId={}", trace.getTraceId(), ex);
        }
    }
}
