package com.hewei.hzyjy.xunzhi.career.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 简历 RAG 检索链路的运行参数。
 *
 * <p>该类只描述代码已支持的能力开关和阈值，不保存任何密钥。部署时由应用配置或环境变量绑定；
 * 修改参数后需要重启后端才会生效。各阶段保持可独立启停，便于在同一批候选简历和 JD 上做
 * 消融实验，量化 HyDE、多查询、向量、BM25、RRF 和重排各自带来的影响。</p>
 */
@Data
@ConfigurationProperties(prefix = "xunzhi-agent.career.rag")
public class CareerRagProperties {

    /**
     * RAG 总开关。
     *
     * <p>关闭后不会执行查询扩展、向量检索、BM25 融合或重排，调用方可将其作为“无 RAG”对照组；
     * 开启后仍可通过下方子开关分别关闭某个阶段。</p>
     */
    private boolean enabled = true;

    /**
     * HyDE 假设文档生成开关。
     *
     * <p>开启时用当前聊天模型把 JD 扩展为一段“理想候选人经历”查询；模型超时或输出为空时会
     * 自动放弃该扩展，继续使用原始 JD，不会中断岗位匹配任务。</p>
     */
    private boolean hydeEnabled = true;

    /**
     * 多查询生成开关。
     *
     * <p>开启后从技术栈、行业/项目经验和同义表达三个视角补充查询；生成失败时只保留原始 JD，
     * 因此关闭该开关可用于测量“查询扩展”本身是否提升召回。</p>
     */
    private boolean multiQueryEnabled = true;

    /** 是否启用 Embedding 向量粗召回和细召回。关闭后仍可由 BM25 提供关键词检索能力。 */
    private boolean vectorEnabled = true;

    /** 是否启用 BM25 关键词召回。它是向量服务超时或语义召回不足时的重要独立兜底通道。 */
    private boolean bm25Enabled = true;

    /**
     * 是否使用 RRF 融合多个查询及召回通道的排序名次。
     *
     * <p>关闭时保留单通道原始顺序，适合与融合策略做对照；开启时不会直接相加向量分数和 BM25
     * 分数，避免异构分数尺度不同造成排序偏置。</p>
     */
    private boolean rrfEnabled = true;

    private double vectorMinScoreCoarse = 0.60;

    private double vectorMinScoreFine = 0.55;

    private int rrfK = 60;

    /**
     * 按简历分片类型配置的 RRF 投票倍率。
     *
     * <p>岗位匹配中技能、项目和经历通常比教育背景更有区分度，因此在 RRF 融合后对对应分片
     * 的贡献进行微调。未配置类型或非法值均使用中性权重 {@code 1.0}，不会影响旧索引。</p>
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
