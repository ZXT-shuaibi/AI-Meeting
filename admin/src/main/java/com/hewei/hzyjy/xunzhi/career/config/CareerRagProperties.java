package com.hewei.hzyjy.xunzhi.career.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "xunzhi-agent.career.rag")
public class CareerRagProperties {

    /** Enables semantic resume retrieval; false switches to the non-RAG experiment baseline. */
    private boolean enabled = true;

    /** 以下开关仅控制对应 RAG 阶段，便于评测各阶段的独立增益。 */
    private boolean hydeEnabled = true;

    private boolean multiQueryEnabled = true;

    private boolean vectorEnabled = true;

    private boolean bm25Enabled = true;

    private boolean rrfEnabled = true;

    private double vectorMinScoreCoarse = 0.60;

    private double vectorMinScoreFine = 0.55;

    private int rrfK = 60;

    /**
     * Optional RRF vote multipliers keyed by resume chunk type. Missing types keep the neutral 1.0 weight.
     */
    private Map<String, Double> chunkWeights = new LinkedHashMap<>();

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
