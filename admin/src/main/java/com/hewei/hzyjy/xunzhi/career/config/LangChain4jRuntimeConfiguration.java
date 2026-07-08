package com.hewei.hzyjy.xunzhi.career.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChain4jRuntimeConfiguration {
    // Spring AI remains the primary model runtime. LangChain4j concrete agents can be
    // registered by starter auto-configuration or future profile-specific @Bean methods;
    // business code talks to them only through LangChain4jAgentAdapter.
}
