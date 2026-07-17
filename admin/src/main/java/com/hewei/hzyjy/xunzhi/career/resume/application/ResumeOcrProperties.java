package com.hewei.hzyjy.xunzhi.career.resume.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "xunzhi-agent.career.ocr")
public class ResumeOcrProperties {

    private boolean enabled = true;

    private String provider = "xunfei-pdf";

    private String appId;

    private String apiSecret;

    private int maxPages = 40;

    private int timeoutSeconds = 90;

    private int pollIntervalSeconds = 5;
}
