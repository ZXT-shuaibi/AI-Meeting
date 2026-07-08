package com.hewei.hzyjy.xunzhi.career.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "xunzhi-agent.career.observability")
public class CareerObservabilityProperties {

    private boolean enabled = true;

    private long redisTtlSeconds = 604800;

    private Map<String, Boolean> sceneEnabled = new HashMap<>();

    public boolean enabledFor(String sceneCode) {
        return enabled && sceneEnabled.getOrDefault(sceneCode, true);
    }
}
