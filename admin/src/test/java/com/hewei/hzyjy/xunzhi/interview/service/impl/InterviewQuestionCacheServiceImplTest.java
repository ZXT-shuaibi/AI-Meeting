package com.hewei.hzyjy.xunzhi.interview.service.impl;

import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionService;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewRadarService;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewScoreService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewQuestionCacheServiceImplTest {

    @Test
    void flowCasScriptUsesRedis32CompatibleMultiFieldHashCommand() throws Exception {
        Field scriptField = InterviewQuestionCacheServiceImpl.class
                .getDeclaredField("FLOW_CAS_UPDATE_SCRIPT");
        scriptField.setAccessible(true);

        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptField.get(null);

        assertTrue(script.getScriptAsString().contains("redis.call('HMSET'"));
    }

    @Test
    void initializesNewFlowWithFourFollowUps() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.expire(anyString(), anyLong(), eq(TimeUnit.HOURS))).thenReturn(true);

        InterviewQuestionCacheServiceImpl service = new InterviewQuestionCacheServiceImpl(
                redisTemplate,
                mock(InterviewQuestionService.class),
                mock(InterviewScoreService.class),
                mock(InterviewRadarService.class)
        );

        service.initInterviewFlow("session-four", 10);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(eq("interview:flow:session:session-four"), payload.capture());
        assertEquals("4", payload.getValue().get("maxFollowUp"));
    }
}
