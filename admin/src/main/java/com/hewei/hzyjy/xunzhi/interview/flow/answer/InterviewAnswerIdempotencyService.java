package com.hewei.hzyjy.xunzhi.interview.flow.answer;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.InterviewAnswerRespDTO;
import com.hewei.hzyjy.xunzhi.interview.config.InterviewAnswerGuardConfiguration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 面试答题请求的幂等状态管理器。
 *
 * <p>处理态用于阻止同一请求并发重复评分，回放态用于在网络重试时复用已成功的结果；
 * 两类状态均存于 Redis 并带过期时间，不承担面试会话的长期事实存储职责。</p>
 */
@Service
@RequiredArgsConstructor
public class InterviewAnswerIdempotencyService {

    private static final String PROCESSING_KEY_PREFIX = "interview:answer:idempotency:processing:";
    private static final String REPLAY_KEY_PREFIX = "interview:answer:idempotency:replay:";

    private final StringRedisTemplate stringRedisTemplate;
    private final InterviewAnswerGuardConfiguration configuration;

    /**
     * 尝试占用一次答题请求：优先返回可回放结果，其次原子创建处理锁；
     * 已被其他请求占用时仅返回处理中，调用方不得重复触发模型评分。
     */
    public TryStartResult tryStart(String sessionId, String requestId) {
        if (StrUtil.isBlank(sessionId) || StrUtil.isBlank(requestId)) {
            return TryStartResult.newRequest();
        }
        String replayPayload = stringRedisTemplate.opsForValue().get(replayKey(sessionId, requestId));
        if (StrUtil.isNotBlank(replayPayload)) {
            InterviewAnswerRespDTO replayResponse = safeParseReplay(replayPayload);
            if (replayResponse != null) {
                return TryStartResult.succeeded(replayResponse);
            }
        }

        Boolean started = stringRedisTemplate.opsForValue().setIfAbsent(
                processingKey(sessionId, requestId),
                "1",
                resolveProcessingExpireSeconds(),
                TimeUnit.SECONDS
        );
        if (Boolean.TRUE.equals(started)) {
            return TryStartResult.newRequest();
        }
        return TryStartResult.processing();
    }

    public void markSucceeded(String sessionId, String requestId, InterviewAnswerRespDTO response) {
        if (StrUtil.isBlank(sessionId) || StrUtil.isBlank(requestId) || response == null) {
            return;
        }
        String replayPayload = JSON.toJSONString(response);
        stringRedisTemplate.opsForValue().set(
                replayKey(sessionId, requestId),
                replayPayload,
                resolveReplayExpireHours(),
                TimeUnit.HOURS
        );
        stringRedisTemplate.delete(processingKey(sessionId, requestId));
    }

    public void clearProcessing(String sessionId, String requestId) {
        if (StrUtil.isBlank(sessionId) || StrUtil.isBlank(requestId)) {
            return;
        }
        stringRedisTemplate.delete(processingKey(sessionId, requestId));
    }

    private InterviewAnswerRespDTO safeParseReplay(String payload) {
        try {
            return JSON.parseObject(payload, InterviewAnswerRespDTO.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String processingKey(String sessionId, String requestId) {
        return PROCESSING_KEY_PREFIX + sessionId + ":" + requestId;
    }

    private String replayKey(String sessionId, String requestId) {
        return REPLAY_KEY_PREFIX + sessionId + ":" + requestId;
    }

    private long resolveProcessingExpireSeconds() {
        Long configured = configuration.getProcessingExpireSeconds();
        long base = configured == null || configured <= 0 ? 120L : configured;
        Long longTail = configuration.getProcessingLongTailExpireSeconds();
        long guard = longTail == null || longTail <= 0 ? base : longTail;
        return Math.max(base, guard);
    }

    private long resolveReplayExpireHours() {
        Long configured = configuration.getReplayExpireHours();
        return configured == null || configured <= 0 ? 24L : configured;
    }

    @Getter
    public static final class TryStartResult {
        private final TryStartStatus status;
        private final InterviewAnswerRespDTO replayResponse;

        private TryStartResult(TryStartStatus status, InterviewAnswerRespDTO replayResponse) {
            this.status = status;
            this.replayResponse = replayResponse;
        }

        public static TryStartResult newRequest() {
            return new TryStartResult(TryStartStatus.NEW, null);
        }

        public static TryStartResult processing() {
            return new TryStartResult(TryStartStatus.PROCESSING, null);
        }

        public static TryStartResult succeeded(InterviewAnswerRespDTO replayResponse) {
            return new TryStartResult(TryStartStatus.SUCCEEDED, replayResponse);
        }
    }

    public enum TryStartStatus {
        NEW,
        PROCESSING,
        SUCCEEDED
    }
}
