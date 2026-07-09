package com.hewei.hzyjy.xunzhi.career.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "xunzhi-agent.career.storage")
public class CareerStorageProperties {

    private ObjectStorage objectStorage = new ObjectStorage();

    @Data
    public static class ObjectStorage {
        private boolean enabled = false;
        private String provider = "local";
        private String baseDir = "${user.home}/.xunzhi-agent/career/object-storage";
        private String publicBaseUrl = "";
        private String endpoint = "";
        private String region = "";
        private String bucketName = "";
    }
}
