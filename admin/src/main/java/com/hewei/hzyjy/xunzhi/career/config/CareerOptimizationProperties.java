package com.hewei.hzyjy.xunzhi.career.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "xunzhi-agent.career.optimization")
public class CareerOptimizationProperties {

    private int maxIterations = 3;

    private double scoreGate = 0.8;
}
