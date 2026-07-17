package com.hewei.hzyjy.xunzhi.career.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "xunzhi-agent.embedding")
public class EmbeddingProperties {

    private boolean enabled;

    private String baseUrl = "";

    private String apiKey = "";

    private String model = "";

    private int dimensions = 1024;

    private long timeoutSeconds = 15;
}
