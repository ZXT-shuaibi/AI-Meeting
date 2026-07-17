package com.hewei.hzyjy.xunzhi.interview.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewQuestionCacheServiceImplTest {

    @Test
    void flowCasScriptUsesRedis32CompatibleMultiFieldHashCommand() throws Exception {
        Field scriptField = InterviewQuestionCacheServiceImpl.class
                .getDeclaredField("FLOW_CAS_UPDATE_SCRIPT");
        scriptField.setAccessible(true);

        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptField.get(null);

        assertTrue(script.getScriptAsString().contains("redis.call('HMSET'"));
    }
}
