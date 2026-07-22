package com.hewei.hzyjy.xunzhi.career.raglab.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** 单次 RAG 实验的不可变运行时配置快照，不修改全局 application 配置。 */
public record RagExperimentRuntimeOptions(
        boolean ragEnabled, boolean hydeEnabled, boolean multiQueryEnabled,
        boolean coarseVectorEnabled, boolean fineVectorEnabled, boolean bm25Enabled,
        boolean rrfEnabled, boolean rerankEnabled, int topK,
        Map<String, Double> chunkWeights) {
    public RagExperimentRuntimeOptions {
        topK = Math.max(1, Math.min(100, topK));
        chunkWeights = Map.copyOf(new LinkedHashMap<>(chunkWeights == null ? Map.of() : chunkWeights));
    }
}
