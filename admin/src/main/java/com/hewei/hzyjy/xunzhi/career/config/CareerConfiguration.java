package com.hewei.hzyjy.xunzhi.career.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        XunzhiLangChain4jProperties.class,
        CareerRagProperties.class,
        CareerObservabilityProperties.class
})
public class CareerConfiguration {
}
