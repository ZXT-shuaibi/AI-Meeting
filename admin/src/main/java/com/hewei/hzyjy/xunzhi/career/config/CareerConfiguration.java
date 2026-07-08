package com.hewei.hzyjy.xunzhi.career.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties({
        XunzhiLangChain4jProperties.class,
        CareerRagProperties.class,
        CareerObservabilityProperties.class
})
public class CareerConfiguration {

    @Bean("careerTaskExecutor")
    public TaskExecutor careerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("career-agent-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(200);
        executor.initialize();
        return executor;
    }
}
