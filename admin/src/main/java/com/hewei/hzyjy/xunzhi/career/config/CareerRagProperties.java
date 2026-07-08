package com.hewei.hzyjy.xunzhi.career.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "xunzhi-agent.career.rag")
public class CareerRagProperties {

    private double vectorMinScoreCoarse = 0.60;

    private double vectorMinScoreFine = 0.55;

    private int rrfK = 60;

    private int coarseRecallLimit = 20;

    private int fineRecallMultiplier = 5;

    private int multiQueryCount = 3;

    private long cacheTtlSeconds = 3600;

    private Rerank rerank = new Rerank();

    @Data
    public static class Rerank {
        private boolean enabled = true;
        private String provider = "dashscope";
        private String model = "qwen3-vl-rerank";
        private String apiKey = "";
    }
}
