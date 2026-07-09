package com.hewei.hzyjy.xunzhi.career.ai;

import com.hewei.hzyjy.xunzhi.career.config.XunzhiLangChain4jProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LangChain4jAgenticSafetyPolicyTest {

    @Test
    void rejectsNativeAgenticListenersBecauseDirectAgentCallsCanMissLangChain4jThreadLocal() {
        XunzhiLangChain4jProperties properties = new XunzhiLangChain4jProperties();
        properties.setAgenticNativeListenersEnabled(true);
        LangChain4jAgenticSafetyPolicy policy = new LangChain4jAgenticSafetyPolicy(properties);

        assertThatThrownBy(policy::assertNativeAgenticListenersDisabled)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LangChain4j Agentic native listeners are disabled")
                .hasMessageContaining("ThreadLocal");
    }

    @Test
    void allowsDefaultAdapterLevelTracingPolicy() {
        XunzhiLangChain4jProperties properties = new XunzhiLangChain4jProperties();
        LangChain4jAgenticSafetyPolicy policy = new LangChain4jAgenticSafetyPolicy(properties);

        assertThatCode(policy::assertNativeAgenticListenersDisabled).doesNotThrowAnyException();
    }
}
