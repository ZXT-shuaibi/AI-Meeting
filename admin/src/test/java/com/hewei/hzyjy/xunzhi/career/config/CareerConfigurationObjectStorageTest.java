package com.hewei.hzyjy.xunzhi.career.config;

import com.hewei.hzyjy.xunzhi.career.resume.application.AliyunOssResumeObjectStorage;
import com.hewei.hzyjy.xunzhi.career.resume.application.LocalResumeObjectStorage;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeObjectStorage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CareerConfigurationObjectStorageTest {

    @Test
    void selectsAliyunOssBackendWhenProviderIsAliyunOss() {
        CareerStorageProperties properties = new CareerStorageProperties();
        properties.getObjectStorage().setEnabled(true);
        properties.getObjectStorage().setProvider("aliyun-oss");
        properties.getObjectStorage().setEndpoint("oss-cn-beijing.aliyuncs.com");
        properties.getObjectStorage().setRegion("cn-beijing");
        properties.getObjectStorage().setBucketName("xunzhi-resume");

        ResumeObjectStorage storage = new CareerConfiguration().resumeObjectStorage(properties);

        assertThat(storage).isInstanceOf(AliyunOssResumeObjectStorage.class);
    }

    @Test
    void keepsLocalBackendAsDefaultWhenEnabledWithoutCloudProvider() {
        CareerStorageProperties properties = new CareerStorageProperties();
        properties.getObjectStorage().setEnabled(true);
        properties.getObjectStorage().setProvider("local");

        ResumeObjectStorage storage = new CareerConfiguration().resumeObjectStorage(properties);

        assertThat(storage).isInstanceOf(LocalResumeObjectStorage.class);
    }

    @Test
    void optimizationPropertiesExposeExpectedDefaults() {
        CareerOptimizationProperties properties = new CareerOptimizationProperties();

        assertThat(properties.getMaxIterations()).isEqualTo(3);
        assertThat(properties.getScoreGate()).isEqualTo(0.8);
    }
}
