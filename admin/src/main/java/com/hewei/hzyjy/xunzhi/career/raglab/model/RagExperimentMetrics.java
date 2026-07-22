package com.hewei.hzyjy.xunzhi.career.raglab.model;

/** 一次 RAG 实验的可复现量化指标快照。 */
public record RagExperimentMetrics(double recallAtK, double precisionAtK, double mrr,
                                   double ndcgAtK, double strongRecallAtK,
                                   int returnedCount, int relevantCount, int strongRelevantCount) { }
