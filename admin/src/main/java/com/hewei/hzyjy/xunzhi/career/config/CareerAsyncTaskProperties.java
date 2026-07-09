package com.hewei.hzyjy.xunzhi.career.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "xunzhi-agent.career.async-task")
public class CareerAsyncTaskProperties {

    private Recovery recovery = new Recovery();

    @Data
    public static class Recovery {
        private boolean enabled = true;
        private long staleAfterSeconds = 1800;
        private int batchSize = 20;
        private long fixedDelayMillis = 30000;
    }
}
