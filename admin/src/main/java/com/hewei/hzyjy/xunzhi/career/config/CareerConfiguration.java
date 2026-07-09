package com.hewei.hzyjy.xunzhi.career.config;

import com.hewei.hzyjy.xunzhi.career.resume.application.AliyunOssResumeObjectStorage;
import com.hewei.hzyjy.xunzhi.career.resume.application.LocalResumeObjectStorage;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeObjectStorage;
import com.hewei.hzyjy.xunzhi.career.skill.CareerSkillRegistry;
import com.hewei.hzyjy.xunzhi.career.skill.ClasspathCareerSkillRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties({
        XunzhiLangChain4jProperties.class,
        CareerRagProperties.class,
        CareerObservabilityProperties.class,
        CareerStorageProperties.class
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

    @Bean
    @ConditionalOnMissingBean
    public ResumeObjectStorage resumeObjectStorage(CareerStorageProperties properties) {
        CareerStorageProperties.ObjectStorage objectStorage = properties.getObjectStorage();
        if (objectStorage == null || !objectStorage.isEnabled()) {
            return ResumeObjectStorage.disabled();
        }
        if ("aliyun-oss".equalsIgnoreCase(objectStorage.getProvider()) || "oss".equalsIgnoreCase(objectStorage.getProvider())) {
            return new AliyunOssResumeObjectStorage(
                    objectStorage.getEndpoint(),
                    objectStorage.getRegion(),
                    objectStorage.getBucketName(),
                    objectStorage.getPublicBaseUrl()
            );
        }
        return new LocalResumeObjectStorage(objectStorage.getProvider(), objectStorage.getBaseDir(), objectStorage.getPublicBaseUrl());
    }

    @Bean
    @ConditionalOnMissingBean
    public CareerSkillRegistry careerSkillRegistry() {
        return ClasspathCareerSkillRegistry.withBuiltIns();
    }
}
