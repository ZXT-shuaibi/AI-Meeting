package com.hewei.hzyjy.xunzhi.career.resume.rag;

import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import com.hewei.hzyjy.xunzhi.career.ai.EmbeddingGateway;
import com.hewei.hzyjy.xunzhi.career.config.CareerRagProperties;
import com.hewei.hzyjy.xunzhi.career.observability.AiToolExecutionEvent;
import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.raglab.model.RagExperimentRuntimeOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.CACHE_KEY_HYDE;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.CACHE_KEY_MQ;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.CHUNK_TYPE_OVERVIEW;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.CHUNK_TYPE_SKILLS;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_CHUNK_INDEX;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_CHUNK_TYPE;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_RESUME_ID;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_USER_ID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeRagService {

    private final ResumeChunker resumeChunker;
    private final EmbeddingGateway embeddingGateway;
    private final ResumeVectorStore vectorStore;
    private final AiGateway aiGateway;
    private final CareerRagProperties ragProperties;
    private final RerankGateway rerankGateway;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<AiTracePublisher> tracePublisherProvider;
    private final ThreadLocal<RagStageTrace> ragStageTrace = new ThreadLocal<>();

    public boolean enabled() {
        return ragProperties.isEnabled();
    }

    /**
     * 将结构化简历切分并写入向量存储。
     *
     * <p>向量化并非成功入库的前提：若 Embedding 服务超时、报错或返回数量不一致，仍会写入
     * 文本分片和元数据，使 BM25 通道能够继续检索。这避免一次外部模型故障使简历完全不可用。</p>
     */
    public List<ResumeChunk> storeCvBO(CvBO cv) {
        List<ResumeChunk> chunks = resumeChunker.chunk(cv);
        if (chunks.isEmpty()) {
            return List.of();
        }
        List<float[]> vectors = embedForStorage(chunks);
        List<ResumeVectorDocument> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            ResumeChunk chunk = chunks.get(i);
            documents.add(ResumeVectorDocument.builder()
                    .id(UUID.randomUUID().toString())
                    .text(chunk.content())
                    .vector(i < vectors.size() && vectors.get(i) != null ? vectors.get(i) : new float[0])
                    .metadata(chunk.metadata())
                    .build());
        }
        vectorStore.addAll(documents);
        return chunks;
    }

    private List<float[]> embedForStorage(List<ResumeChunk> chunks) {
        try {
            List<float[]> vectors = embeddingGateway.embedAll(chunks.stream().map(ResumeChunk::content).toList());
            if (vectors != null && vectors.size() == chunks.size()) {
                return vectors;
            }
            String message = "Embedding gateway returned mismatched vector count";
            log.warn("{}；将仅保存文本分片供 BM25 兜底检索使用。预期向量数={}，实际向量数={}",
                    message,
                    chunks.size(), vectors == null ? 0 : vectors.size());
            publishRagToolEvent("rag-embedding-store", "RESUME_ANALYSIS", "storeCvBO",
                    "expected=" + chunks.size(), false, message, Map.of("expected", chunks.size(), "actual", vectors == null ? 0 : vectors.size()));
        } catch (Exception ex) {
            log.warn("简历分片存储时向量化失败，已仅保存文本分片供 BM25 兜底检索使用。", ex);
            publishRagToolEvent("rag-embedding-store", "RESUME_ANALYSIS", "storeCvBO",
                    "chunks=" + chunks.size(), false, ex.getMessage(), Map.of("chunkCount", chunks.size()));
        }
        return chunks.stream().map(chunk -> new float[0]).toList();
    }

    public List<String> retrieveTemplates(String query, int limit) {
        return retrieveTemplates(query, limit, null, Set.of());
    }

    public List<String> retrieveTemplates(String query, int limit, Long userId, Set<String> allowedResumeIds) {
        return retrieveTemplates(query, limit, userId, allowedResumeIds, ragProperties.isEnabled());
    }

    public List<String> retrieveTemplates(String query, int limit, Long userId, Set<String> allowedResumeIds, boolean enabled) {
        return retrieveTemplates(query, limit, userId, allowedResumeIds, enabled, null, "RESUME_TEMPLATE_RAG");
    }

    /**
     * 在调用方提供主业务 Trace 时，将每个 RAG 阶段挂到该 Trace；未提供时保留独立调用的兼容行为。
     */
    public List<String> retrieveTemplates(
            String query,
            int limit,
            Long userId,
            Set<String> allowedResumeIds,
            boolean enabled,
            String businessTraceId,
            String sceneCode) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        long start = System.currentTimeMillis();
        if (!enabled) {
            publishRecallMetric(query, userId, allowedResumeIds == null ? 0 : allowedResumeIds.size(), 0, start,
                    "resume-template-retrieval", false);
            return List.of();
        }
        String ragTraceId = beginRagStageTrace(businessTraceId, sceneCode);
        Set<String> resumeScope = allowedResumeIds == null ? Set.of() : allowedResumeIds;
        Map<String, String> metadataFilters = userId == null ? Map.of() : Map.of(META_USER_ID, String.valueOf(userId));
        List<String> allQueries = expandQueries(query);

        Set<String> candidateResumeIds = hierarchicalCoarseSearch(allQueries, ragProperties.getCoarseRecallLimit(), resumeScope, metadataFilters);
        if (candidateResumeIds.isEmpty() && !resumeScope.isEmpty()) {
            candidateResumeIds = new LinkedHashSet<>(resumeScope);
        }
        List<RetrievedChunk> fusedChunks = hybridFineSearch(
                allQueries,
                candidateResumeIds,
                metadataFilters,
                Math.max(limit, 1) * ragProperties.getFineRecallMultiplier()
        );
        List<String> augmented = contextAugment(fusedChunks, Math.max(limit * 2, limit));
        List<String> results = rerank(query, augmented, limit);
        log.info("简历 RAG 召回完成。查询长度={}，用户编号={}，简历范围数={}，候选数={}，结果数={}，耗时毫秒={}",
                query.length(), userId, resumeScope.size(), candidateResumeIds.size(), results.size(), System.currentTimeMillis() - start);
        finishRagStageTrace(ragTraceId, start);
        publishRecallMetric(query, userId, resumeScope.size(), results.size(), start, "resume-template-retrieval", true);
        return results;
    }

    /**
     * 在用户明确勾选的简历范围内执行混合召回，并保留简历身份用于版本之间的岗位匹配比较。
     *
     * <p>无论向量、BM25、RRF 或重排阶段如何降级，最终都不得返回 {@code allowedResumeIds}
     * 范围以外的简历，避免候选集合被历史数据或其他上传任务污染。</p>
     */
    public List<ResumeRagMatch> retrieveResumeMatches(String query, int limit, Long userId, Set<String> allowedResumeIds) {
        return retrieveResumeMatches(query, limit, userId, allowedResumeIds, ragProperties.isEnabled());
    }

    /**
     * 执行岗位匹配召回的完整编排。
     *
     * <p>处理顺序为：原始 JD -> HyDE/多查询扩展 -> 向量粗筛 -> 向量与 BM25 细召回 -> RRF 融合
     * -> 分片类型加权 -> 简历级聚合 -> 可选重排。每个阶段都记录耗时和降级原因；任何一个外部
     * 通道失败都只降级该阶段，始终不突破调用方传入的候选简历范围。</p>
     */
    public List<ResumeRagMatch> retrieveResumeMatches(String query, int limit, Long userId, Set<String> allowedResumeIds, boolean enabled) {
        return retrieveResumeMatches(query, limit, userId, allowedResumeIds, enabled, null, "JOB_MATCH_RAG");
    }

    /**
     * RAG 实验室的检索入口。实验配置与业务 Trace 仅作用于当前调用，普通岗位匹配仍使用原有入口。
     * 当前阶段先将 RAG 总开关、Top-K 和实验 Trace 接入既有稳定链路；其余细粒度开关会在内部阶段
     * 逐项改为读取 {@link RagExperimentRuntimeOptions}，避免一次性改变线上召回行为。
     */
    public List<ResumeRagMatch> retrieveExperimentMatches(
            String query, Long userId, Set<String> allowedResumeIds,
            RagExperimentRuntimeOptions options, String experimentTraceId) {
        if (options == null) {
            throw new IllegalArgumentException("RAG 实验运行参数不能为空");
        }
        return retrieveResumeMatches(query, options.topK(), userId, allowedResumeIds,
                options.ragEnabled(), experimentTraceId, "RAG_EXPERIMENT");
    }

    /** 见 {@link #retrieveTemplates(String, int, Long, Set, boolean, String, String)} 的 Trace 关联约定。 */
    public List<ResumeRagMatch> retrieveResumeMatches(
            String query,
            int limit,
            Long userId,
            Set<String> allowedResumeIds,
            boolean enabled,
            String businessTraceId,
            String sceneCode) {
        if (query == null || query.isBlank() || allowedResumeIds == null || allowedResumeIds.isEmpty()) {
            return List.of();
        }
        long start = System.currentTimeMillis();
        String ragTraceId = beginRagStageTrace(businessTraceId, sceneCode);
        if (!enabled) {
            publishRecallMetric(query, userId, allowedResumeIds.size(), 0, start, "resume-job-match-retrieval", false);
            return List.of();
        }
        Set<String> resumeScope = new LinkedHashSet<>(allowedResumeIds);
        Map<String, String> metadataFilters = userId == null ? Map.of() : Map.of(META_USER_ID, String.valueOf(userId));
        log.info("RAG阶段诊断 traceId={} 阶段=执行计划 RAG启用=true 候选简历数={} TopK={} HyDE启用={} 多查询启用={} 多查询视角=[技能技术栈,行业项目经验,同义表达] 向量召回启用={} BM25启用={} RRF融合启用={} Rerank启用={} Rerank供应商={} Rerank模型={}",
                ragTraceId, resumeScope.size(), limit, ragProperties.isHydeEnabled(), ragProperties.isMultiQueryEnabled(),
                ragProperties.isVectorEnabled(), ragProperties.isBm25Enabled(), ragProperties.isRrfEnabled(),
                ragProperties.getRerank().isEnabled(), ragProperties.getRerank().getProvider(), ragProperties.getRerank().getModel());
        long queryExpansionStart = System.currentTimeMillis();
        List<String> allQueries = new ArrayList<>();
        allQueries.add(query);
        String hyde = generateHyDE(query);
        if (hyde != null && !hyde.isBlank()) {
            allQueries.add(hyde);
        }
        allQueries.addAll(generateMultiQueries(query));
        log.info("RAG阶段诊断 traceId={} 阶段=查询扩展 耗时毫秒={} HyDE实际生效={} 多查询实际数量={} 总查询数={} 查询摘要={}",
                ragTraceId, System.currentTimeMillis() - queryExpansionStart, hyde != null && !hyde.isBlank(),
                Math.max(0, allQueries.size() - 1 - (hyde == null || hyde.isBlank() ? 0 : 1)), allQueries.size(),
                allQueries.stream().map(this::digest).toList());
        log.info("RAG 查询扩展完成：HyDE生效={}，总查询数={}", hyde != null && !hyde.isBlank(), allQueries.size());
        long coarseRecallStart = System.currentTimeMillis();
        Set<String> candidateResumeIds = hierarchicalCoarseSearch(allQueries, ragProperties.getCoarseRecallLimit(), resumeScope, metadataFilters);
        if (candidateResumeIds.isEmpty()) {
            candidateResumeIds = resumeScope;
        }
        log.info("RAG阶段诊断 traceId={} 阶段=向量粗召回 耗时毫秒={} 向量启用={} 粗召回阈值={} 粗召回上限={} 候选结果数={} 发生全量兜底={}",
                ragTraceId, System.currentTimeMillis() - coarseRecallStart, ragProperties.isVectorEnabled(),
                ragProperties.getVectorMinScoreCoarse(), ragProperties.getCoarseRecallLimit(), candidateResumeIds.size(),
                candidateResumeIds.size() == resumeScope.size());
        log.info("RAG 粗召回完成：候选简历数={}，候选简历ID={}", candidateResumeIds.size(), candidateResumeIds);
        long fineRecallStart = System.currentTimeMillis();
        List<RetrievedChunk> chunks = hybridFineSearch(
                allQueries,
                candidateResumeIds,
                metadataFilters,
                Math.max(limit, 1) * ragProperties.getFineRecallMultiplier());
        log.info("RAG阶段诊断 traceId={} 阶段=细召回与融合 耗时毫秒={} 向量启用={} BM25启用={} RRF启用={} 细召回阈值={} 命中片段数={}",
                ragTraceId, System.currentTimeMillis() - fineRecallStart, ragProperties.isVectorEnabled(),
                ragProperties.isBm25Enabled(), ragProperties.isRrfEnabled(), ragProperties.getVectorMinScoreFine(), chunks.size());
        log.info("RAG 细召回与RRF融合完成：命中片段数={}", chunks.size());
        Map<String, List<RetrievedChunk>> byResume = chunks.stream()
                .collect(Collectors.groupingBy(RetrievedChunk::resumeId, LinkedHashMap::new, Collectors.toList()));
        List<ResumeRagMatch> preRerankMatches = resumeScope.stream()
                .map(resumeId -> {
                    List<RetrievedChunk> evidence = byResume.getOrDefault(resumeId, List.of());
                    double score = evidence.stream().mapToDouble(RetrievedChunk::rrfScore).sum();
                    List<String> snippets = evidence.stream()
                            .sorted(Comparator.comparingDouble(RetrievedChunk::rrfScore).reversed())
                            .map(RetrievedChunk::text)
                            .filter(text -> text != null && !text.isBlank())
                            .map(text -> text.length() > 180 ? text.substring(0, 180) + "..." : text)
                            .limit(3)
                            .toList();
                    return new ResumeRagMatch(resumeId, score, snippets);
                })
                .sorted(Comparator.comparingDouble(ResumeRagMatch::score).reversed())
                .toList();
        long rerankStart = System.currentTimeMillis();
        List<ResumeRagMatch> matches = rerankResumeMatches(query, preRerankMatches, limit);
        log.info("RAG阶段诊断 traceId={} 阶段=Rerank重排 耗时毫秒={} Rerank启用={} 供应商={} 模型={} 输入简历数={} 输出简历数={}",
                ragTraceId, System.currentTimeMillis() - rerankStart, ragProperties.getRerank().isEnabled(),
                ragProperties.getRerank().getProvider(), ragProperties.getRerank().getModel(), preRerankMatches.size(), matches.size());
        log.info("RAG 岗位匹配排序完成：返回简历数={}，排序简历ID={}", matches.size(), matches.stream().map(ResumeRagMatch::resumeId).toList());
        finishRagStageTrace(ragTraceId, start);
        publishRecallMetric(query, userId, resumeScope.size(), matches.size(), start, "resume-job-match-retrieval", true);
        return matches;
    }

    private List<String> expandQueries(String query) {
        List<String> allQueries = new ArrayList<>();
        allQueries.add(query);
        String hyde = generateHyDE(query);
        if (hyde != null && !hyde.isBlank()) {
            allQueries.add(hyde);
        }
        allQueries.addAll(generateMultiQueries(query));
        return allQueries;
    }

    private String generateHyDE(String query) {
        long startedAt = System.nanoTime();
        if (!ragProperties.isHydeEnabled()) {
            logRagStage("HyDE", false, startedAt, 1, 0, "未使用", "阶段已关闭");
            return null;
        }
        String cacheKey = CACHE_KEY_HYDE + digest(query);
        String cached = getCached(cacheKey);
        if (cached != null) {
            logRagStage("HyDE", true, startedAt, 1, 1, "命中", "");
            return cached;
        }
        try {
            String result = aiGateway.chat(AiPromptRequest.builder()
                    .sceneCode("RESUME_RAG_HYDE")
                    .systemPrompt("Generate a concise hypothetical resume summary and skill list matching the job description.")
                    .userPrompt(query)
                    .build()).content();
            setCached(cacheKey, result);
            logRagStage("HyDE", true, startedAt, 1, result == null || result.isBlank() ? 0 : 1, "未命中", "");
            return result;
        } catch (Exception ex) {
            log.warn("HyDE 假设文档生成失败，已使用原始查询继续检索。", ex);
            publishRagToolEvent("rag-hyde", "JD_ALIGNMENT", query,
                    "fallback=original-query", false, ex.getMessage(), Map.of("queryDigest", digest(query)));
            logRagStage("HyDE", true, startedAt, 1, 0, "未命中", "原始查询");
            return null;
        }
    }

    private List<String> generateMultiQueries(String query) {
        long startedAt = System.nanoTime();
        if (!ragProperties.isMultiQueryEnabled()) {
            logRagStage("Multi-query", false, startedAt, 1, 0, "未使用", "阶段已关闭");
            return List.of();
        }
        String cacheKey = CACHE_KEY_MQ + digest(query);
        String cached = getCached(cacheKey);
        if (cached != null) {
            List<String> queries = cached.lines().filter(line -> !line.isBlank()).limit(ragProperties.getMultiQueryCount()).toList();
            logRagStage("Multi-query", true, startedAt, 1, queries.size(), "命中", "");
            return queries;
        }
        try {
            String result = aiGateway.chat(AiPromptRequest.builder()
                    .sceneCode("RESUME_RAG_MULTI_QUERY")
                    .systemPrompt("Generate three semantically related resume search queries: skills, industry experience, synonyms. One per line.")
                    .userPrompt(query)
                    .build()).content();
            List<String> queries = result.lines()
                    .map(String::trim)
                    .filter(line -> line.length() > 5)
                    .limit(ragProperties.getMultiQueryCount())
                    .toList();
            if (!queries.isEmpty()) {
                setCached(cacheKey, String.join("\n", queries));
            }
            logRagStage("Multi-query", true, startedAt, 1, queries.size(), "未命中", "");
            return queries;
        } catch (Exception ex) {
            log.warn("多查询生成失败，已继续使用现有查询。", ex);
            publishRagToolEvent("rag-multi-query", "JD_ALIGNMENT", query,
                    "fallback=empty", false, ex.getMessage(), Map.of("queryDigest", digest(query)));
            logRagStage("Multi-query", true, startedAt, 1, 0, "未命中", "空查询集");
            return List.of();
        }
    }

    private void logRagStage(
            String stage,
            boolean enabled,
            long startedAt,
            int inputCount,
            int outputCount,
            String cacheStatus,
            String fallbackReason) {
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        recordRagStage(stage, enabled, durationMs, inputCount, outputCount, cacheStatus, fallbackReason);
    }

    private void logRagStageDuration(
            String stage,
            boolean enabled,
            long durationNanos,
            int inputCount,
            int outputCount,
            String cacheStatus,
            String fallbackReason) {
        recordRagStage(stage, enabled, durationNanos / 1_000_000, inputCount, outputCount, cacheStatus, fallbackReason);
    }

    private String beginRagStageTrace(String businessTraceId, String sceneCode) {
        String traceId = businessTraceId == null || businessTraceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : businessTraceId;
        ragStageTrace.set(new RagStageTrace(traceId, sceneCode));
        return traceId;
    }

    private void finishRagStageTrace(String traceId, long requestStartedAt) {
        RagStageTrace trace = ragStageTrace.get();
        if (trace == null) {
            return;
        }
        String stages = trace.stages.stream()
                .map(stage -> "|  [*] %-10s 启用=%-5s 耗时毫秒=%-6d 输入数量=%-3d 输出数量=%-3d 缓存=%s 回退=%s".formatted(
                        stage.name, stage.enabled, stage.durationMs, stage.inputCount, stage.outputCount,
                        stage.cacheStatus, stage.fallbackReason))
                .collect(Collectors.joining("\n"));
        log.info("""

+==================== [RAG调用 {}] ====================+
|  总耗时毫秒={}
{}
+================== [RAG调用结束] ==================+
""", traceId, System.currentTimeMillis() - requestStartedAt, stages);
        publishRagStageEvents(trace);
        ragStageTrace.remove();
    }

    private void recordRagStage(
            String stage,
            boolean enabled,
            long durationMs,
            int inputCount,
            int outputCount,
            String cacheStatus,
            String fallbackReason) {
        RagStageTrace trace = ragStageTrace.get();
        if (trace != null) {
            trace.stages.add(new RagStageMetric(stage, enabled, durationMs, inputCount, outputCount, cacheStatus, fallbackReason));
            return;
        }
        log.info("RAG阶段指标 阶段={} 启用={} 耗时毫秒={} 输入数量={} 输出数量={} 缓存状态={} 回退原因={}",
                stage, enabled, durationMs, inputCount, outputCount, cacheStatus, fallbackReason);
    }

    private void publishRagStageEvents(RagStageTrace trace) {
        AiTracePublisher publisher = tracePublisherProvider.getIfAvailable();
        if (publisher == null) {
            return;
        }
        for (int index = 0; index < trace.stages.size(); index++) {
            RagStageMetric stage = trace.stages.get(index);
            boolean success = !isRagStageFallback(stage.fallbackReason);
            publisher.tool(new AiToolExecutionEvent(
                    trace.traceId,
                    null,
                    null,
                    trace.sceneCode,
                    "RAG_STAGE_" + stage.name,
                    "",
                    "",
                    success,
                    stage.durationMs,
                    index + 1,
                    success ? null : stage.fallbackReason,
                    Instant.now(),
                    Map.of(
                            "enabled", stage.enabled,
                            "inputCount", stage.inputCount,
                            "outputCount", stage.outputCount,
                            "cacheStatus", stage.cacheStatus,
                            "fallbackReason", stage.fallbackReason == null ? "" : stage.fallbackReason
                    )
            ));
        }
    }

    private boolean isRagStageFallback(String reason) {
        if (reason == null || reason.isBlank() || "阶段已关闭".equals(reason) || "稳定去重合并".equals(reason)) {
            return false;
        }
        return reason.contains("失败") || reason.contains("回退") || "原始查询".equals(reason) || "空查询集".equals(reason);
    }

    private static final class RagStageTrace {
        private final String traceId;
        private final String sceneCode;
        private final List<RagStageMetric> stages = new ArrayList<>();

        private RagStageTrace(String traceId, String sceneCode) {
            this.traceId = traceId;
            this.sceneCode = sceneCode;
        }
    }

    private record RagStageMetric(
            String name,
            boolean enabled,
            long durationMs,
            int inputCount,
            int outputCount,
            String cacheStatus,
            String fallbackReason) {
    }

    private Set<String> hierarchicalCoarseSearch(List<String> queries, int targetLimit, Set<String> resumeScope, Map<String, String> metadataFilters) {
        long startedAt = System.nanoTime();
        Set<String> resumeIds = new LinkedHashSet<>();
        if (!ragProperties.isVectorEnabled()) {
            log.info("RAG 向量粗召回已关闭，将使用所选简历范围作为候选集。");
            logRagStage("粗向量召回", false, startedAt, queries.size(), 0, "不适用", "阶段已关闭");
            return resumeIds;
        }
        boolean fallbackOccurred = false;
        Set<String> coarseTypes = Set.of(CHUNK_TYPE_OVERVIEW, CHUNK_TYPE_SKILLS);
        for (String query : queries) {
            List<ResumeVectorMatch> matches;
            try {
                matches = vectorStore.search(
                        embeddingGateway.embed(query),
                        coarseTypes,
                        resumeScope,
                        metadataFilters,
                        ragProperties.getVectorMinScoreCoarse(),
                        targetLimit * 2
                );
            } catch (Exception ex) {
                fallbackOccurred = true;
                log.warn("向量粗召回失败，BM25 细召回仍将继续执行。查询摘要={}", digest(query), ex);
                publishRagToolEvent("rag-vector-coarse", "JD_ALIGNMENT", query,
                        "fallback=bm25-fine", false, ex.getMessage(), Map.of("queryDigest", digest(query), "targetLimit", targetLimit));
                continue;
            }
            for (ResumeVectorMatch match : matches) {
                String resumeId = match.document().metadata().get(META_RESUME_ID);
                if (resumeId != null && !resumeId.isBlank()) {
                    resumeIds.add(resumeId);
                    if (resumeIds.size() >= targetLimit) {
                        logRagStage("粗向量召回", true, startedAt, queries.size(), resumeIds.size(), "不适用",
                                fallbackOccurred ? "部分查询失败" : "");
                        return resumeIds;
                    }
                }
            }
        }
        logRagStage("粗向量召回", true, startedAt, queries.size(), resumeIds.size(), "不适用",
                fallbackOccurred ? "部分查询失败" : "");
        return resumeIds;
    }

    private List<RetrievedChunk> hybridFineSearch(List<String> queries, Set<String> candidateResumeIds, Map<String, String> metadataFilters, int limit) {
        List<List<RetrievedChunk>> rankedLists = new ArrayList<>();
        long vectorDurationNanos = 0;
        long bm25DurationNanos = 0;
        int vectorOutputCount = 0;
        int bm25OutputCount = 0;
        for (String query : queries) {
            if (ragProperties.isVectorEnabled()) {
                long vectorStartedAt = System.nanoTime();
                try {
                    List<ResumeVectorMatch> vectorMatches = vectorStore.search(
                            embeddingGateway.embed(query),
                            Set.of(),
                            candidateResumeIds,
                            metadataFilters,
                            ragProperties.getVectorMinScoreFine(),
                            limit
                    );
                    List<RetrievedChunk> vectorRanked = vectorMatches.stream().map(this::toRetrievedChunk).toList();
                    vectorOutputCount += vectorRanked.size();
                    if (!vectorRanked.isEmpty()) {
                        rankedLists.add(vectorRanked);
                    }
                } catch (Exception ex) {
                    log.warn("向量细召回失败，已继续使用独立 BM25 通道。查询摘要={}", digest(query), ex);
                    publishRagToolEvent("rag-vector-fine", "JD_ALIGNMENT", query,
                            "fallback=bm25", false, ex.getMessage(), Map.of("queryDigest", digest(query), "limit", limit));
                } finally {
                    vectorDurationNanos += System.nanoTime() - vectorStartedAt;
                }
            }
            if (ragProperties.isBm25Enabled()) {
                long bm25StartedAt = System.nanoTime();
                try {
                    List<RetrievedChunk> bm25Ranked = bm25Recall(query, candidateResumeIds, metadataFilters, limit);
                    bm25OutputCount += bm25Ranked.size();
                    if (!bm25Ranked.isEmpty()) {
                        rankedLists.add(bm25Ranked);
                    }
                } finally {
                    bm25DurationNanos += System.nanoTime() - bm25StartedAt;
                }
            }
        }
        logRagStageDuration("细向量召回", ragProperties.isVectorEnabled(), vectorDurationNanos,
                queries.size(), vectorOutputCount, "不适用", "");
        logRagStageDuration("BM25召回", ragProperties.isBm25Enabled(), bm25DurationNanos,
                queries.size(), bm25OutputCount, "不适用", "");
        long fusionStartedAt = System.nanoTime();
        List<RetrievedChunk> fused = ragProperties.isRrfEnabled()
                ? rrfFusionChunks(rankedLists, ragProperties.getRrfK(), limit)
                : mergeRankedChunksWithoutRrf(rankedLists, limit);
        logRagStage("RRF融合", ragProperties.isRrfEnabled(), fusionStartedAt,
                rankedLists.size(), fused.size(), "不适用", ragProperties.isRrfEnabled()
                        ? "chunkWeights=" + ragProperties.getChunkWeights()
                        : "稳定去重合并");
        return fused;
    }

    private List<RetrievedChunk> bm25Recall(
            String query,
            Set<String> candidateResumeIds,
            Map<String, String> metadataFilters,
            int limit) {
        List<ResumeVectorDocument> documents;
        try {
            documents = vectorStore.findByResumeIds(candidateResumeIds);
        } catch (Exception ex) {
            log.warn("BM25 召回来源查询失败。查询摘要={}", digest(query), ex);
            publishRagToolEvent("rag-bm25-source", "JD_ALIGNMENT", query,
                    "fallback=empty", false, ex.getMessage(), Map.of("queryDigest", digest(query), "candidateCount", candidateResumeIds == null ? 0 : candidateResumeIds.size()));
            return List.of();
        }
        List<ResumeVectorDocument> filtered = documents.stream()
                .filter(document -> matchesMetadata(document.metadata(), metadataFilters))
                .toList();
        if (filtered.isEmpty()) {
            return List.of();
        }
        List<String> texts = filtered.stream().map(ResumeVectorDocument::text).distinct().toList();
        Map<String, Double> bm25Scores = Bm25Scorer.score(texts, query);
        return filtered.stream()
                .map(document -> toRetrievedChunk(document, bm25Scores.getOrDefault(document.text(), 0.0)))
                .filter(chunk -> chunk.rrfScore() > 0.0)
                .sorted(Comparator.comparingDouble(RetrievedChunk::rrfScore).reversed())
                .limit(limit)
                .toList();
    }

    private List<RetrievedChunk> rrfFusionChunks(List<List<RetrievedChunk>> rankedLists, int k, int limit) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, RetrievedChunk> chunks = new HashMap<>();
        for (List<RetrievedChunk> rankedList : rankedLists) {
            for (int i = 0; i < rankedList.size(); i++) {
                RetrievedChunk chunk = rankedList.get(i);
                String key = chunk.resumeId() + "|" + chunk.chunkType() + "|" + chunk.chunkIndex();
                scores.merge(key, chunkWeight(chunk.chunkType()) / (k + i + 1), Double::sum);
                chunks.putIfAbsent(key, chunk);
            }
        }
        return scores.entrySet().stream()
                .map(entry -> chunks.get(entry.getKey()).withRrfScore(entry.getValue()))
                .sorted(Comparator.comparingDouble(RetrievedChunk::rrfScore).reversed())
                .limit(limit)
                .toList();
    }

    private double chunkWeight(String chunkType) {
        Double configuredWeight = ragProperties.getChunkWeights() == null
                ? null
                : ragProperties.getChunkWeights().get(chunkType);
        if (configuredWeight == null) {
            return 1.0;
        }
        if (!Double.isFinite(configuredWeight) || configuredWeight <= 0.0) {
            log.warn("已忽略无效的 RRF 分片权重。分片类型={}，权重={}；已使用默认值 1.0", chunkType, configuredWeight);
            return 1.0;
        }
        return configuredWeight;
    }

    /** RRF 关闭时保留各召回通道的原始排序，避免直接比较向量与 BM25 的异构分数。 */
    private List<RetrievedChunk> mergeRankedChunksWithoutRrf(List<List<RetrievedChunk>> rankedLists, int limit) {
        Map<String, RetrievedChunk> uniqueChunks = new LinkedHashMap<>();
        for (List<RetrievedChunk> rankedList : rankedLists) {
            for (RetrievedChunk chunk : rankedList) {
                String key = chunk.resumeId() + "|" + chunk.chunkType() + "|" + chunk.chunkIndex();
                uniqueChunks.putIfAbsent(key, chunk);
            }
        }
        return uniqueChunks.values().stream().limit(limit).toList();
    }

    private List<ResumeRagMatch> rerankResumeMatches(String query, List<ResumeRagMatch> matches, int limit) {
        int resultLimit = Math.max(1, limit);
        if (!ragProperties.getRerank().isEnabled()) {
            return matches.stream().limit(resultLimit).toList();
        }
        int candidateLimit = Math.max(resultLimit, resultLimit * ragProperties.getFineRecallMultiplier());
        Map<String, ResumeRagMatch> candidates = new LinkedHashMap<>();
        matches.stream()
                .filter(match -> !match.evidence().isEmpty())
                .limit(candidateLimit)
                .forEach(match -> candidates.put("[resumeId=" + match.resumeId() + "]\n"
                        + String.join("\n", match.evidence()), match));
        if (candidates.isEmpty()) {
            return matches.stream().limit(resultLimit).toList();
        }
        List<String> rerankedDocuments = rerank(query, new ArrayList<>(candidates.keySet()), resultLimit);
        log.info("RAG 岗位重排完成：输入候选数={}，重排返回数={}，请求TopK={}",
                candidates.size(), rerankedDocuments.size(), resultLimit);
        List<ResumeRagMatch> reranked = rerankedDocuments.stream()
                .map(candidates::get)
                .filter(match -> match != null)
                .limit(resultLimit)
                .toList();
        if (reranked.isEmpty()) {
            return matches.stream().limit(resultLimit).toList();
        }
        Map<String, ResumeRagMatch> completed = new LinkedHashMap<>();
        reranked.forEach(match -> completed.put(match.resumeId(), match));
        matches.forEach(match -> completed.putIfAbsent(match.resumeId(), match));
        return completed.values().stream().limit(resultLimit).toList();
    }

    private List<String> contextAugment(List<RetrievedChunk> chunks, int limit) {
        Map<String, List<RetrievedChunk>> grouped = chunks.stream()
                .collect(Collectors.groupingBy(RetrievedChunk::resumeId, LinkedHashMap::new, Collectors.toList()));
        return grouped.values().stream()
                .sorted(Comparator.comparingDouble(this::bestScore).reversed())
                .map(this::mergeResumeChunks)
                .filter(text -> !text.isBlank())
                .limit(limit)
                .toList();
    }

    private List<String> rerank(String query, List<String> candidates, int limit) {
        long startedAt = System.nanoTime();
        if (candidates.isEmpty()) {
            logRagStage("Rerank", ragProperties.getRerank().isEnabled(), startedAt, 0, 0, "不适用", "无候选");
            return List.of();
        }
        if (!ragProperties.getRerank().isEnabled()) {
            List<String> result = candidates.stream().limit(limit).toList();
            logRagStage("Rerank", false, startedAt, candidates.size(), result.size(), "不适用", "阶段已关闭");
            return result;
        }
        try {
            List<String> reranked = rerankGateway.rerank(query, candidates, limit);
            if (reranked != null && !reranked.isEmpty()) {
                logRagStage("Rerank", true, startedAt, candidates.size(), reranked.size(), "不适用", "");
                return reranked;
            }
        } catch (Exception ex) {
            log.warn("外部重排服务调用失败，已使用 BM25 兜底排序。", ex);
            publishRagToolEvent("rag-rerank", "JD_ALIGNMENT", query,
                    "fallback=bm25", false, ex.getMessage(), Map.of("candidateCount", candidates.size(), "limit", limit));
        }
        Map<String, Double> bm25 = Bm25Scorer.score(candidates, query);
        List<String> result = candidates.stream()
                .sorted(Comparator.comparingDouble((String text) -> bm25.getOrDefault(text, 0.0)).reversed())
                .limit(limit)
                .toList();
        logRagStage("Rerank", true, startedAt, candidates.size(), result.size(), "不适用", "BM25回退");
        return result;
    }

    private RetrievedChunk toRetrievedChunk(ResumeVectorMatch match) {
        Map<String, String> metadata = match.document().metadata();
        return new RetrievedChunk(
                match.document().text(),
                metadata.getOrDefault(META_RESUME_ID, ""),
                metadata.getOrDefault(META_CHUNK_TYPE, ""),
                parseInt(metadata.get(META_CHUNK_INDEX)),
                match.score()
        );
    }

    private RetrievedChunk toRetrievedChunk(ResumeVectorDocument document, double score) {
        Map<String, String> metadata = document.metadata();
        return new RetrievedChunk(
                document.text(),
                metadata.getOrDefault(META_RESUME_ID, ""),
                metadata.getOrDefault(META_CHUNK_TYPE, ""),
                parseInt(metadata.get(META_CHUNK_INDEX)),
                score
        );
    }

    private boolean matchesMetadata(Map<String, String> metadata, Map<String, String> metadataFilters) {
        if (metadataFilters == null || metadataFilters.isEmpty()) {
            return true;
        }
        Map<String, String> safeMetadata = metadata == null ? Map.of() : metadata;
        for (Map.Entry<String, String> entry : metadataFilters.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().equals(safeMetadata.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private double bestScore(List<RetrievedChunk> chunks) {
        return chunks.stream().mapToDouble(RetrievedChunk::rrfScore).max().orElse(0.0);
    }

    private String mergeResumeChunks(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .collect(Collectors.toMap(
                        chunk -> chunk.chunkType() + "|" + chunk.chunkIndex(),
                        chunk -> chunk,
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .sorted(Comparator.comparingInt(chunk -> chunkTypeOrder(chunk.chunkType())))
                .map(RetrievedChunk::text)
                .collect(Collectors.joining("\n\n"));
    }

    private int chunkTypeOrder(String type) {
        return switch (type == null ? "" : type) {
            case ResumeRagConstants.CHUNK_TYPE_OVERVIEW -> 0;
            case ResumeRagConstants.CHUNK_TYPE_SUMMARY -> 1;
            case ResumeRagConstants.CHUNK_TYPE_SKILLS -> 2;
            case ResumeRagConstants.CHUNK_TYPE_EXPERIENCE -> 3;
            case ResumeRagConstants.CHUNK_TYPE_PROJECT -> 4;
            case ResumeRagConstants.CHUNK_TYPE_EDUCATION -> 5;
            default -> 99;
        };
    }

    private String getCached(String key) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception ex) {
            log.debug("RAG 缓存读取失败。缓存键={}", key, ex);
            return null;
        }
    }

    private void setCached(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ragProperties.getCacheTtlSeconds()));
        } catch (Exception ex) {
            log.debug("RAG 缓存写入失败。缓存键={}", key, ex);
        }
    }

    private void publishRagToolEvent(
            String toolName,
            String sceneCode,
            String input,
            String output,
            boolean success,
            String errorMessage,
            Map<String, Object> metadata) {
        AiTracePublisher tracePublisher = tracePublisherProvider.getIfAvailable();
        if (tracePublisher == null) {
            return;
        }
        tracePublisher.tool(new AiToolExecutionEvent(
                UUID.randomUUID().toString(),
                "resume-rag",
                null,
                sceneCode,
                toolName,
                abbreviate(input, 1000),
                abbreviate(output, 2000),
                success,
                0,
                0,
                errorMessage,
                Instant.now(),
                metadata == null ? Map.of() : metadata
        ));
    }

    private void publishRecallMetric(String query, Long userId, int resumeScope, int resultCount, long startMillis, String toolName, boolean ragEnabled) {
        long durationMs = Math.max(0, System.currentTimeMillis() - startMillis);
        log.info("性能指标 指标名称=RAG召回完成 RAG开关={} 召回类型={} 用户编号={} 简历范围数量={} 结果数量={} 召回耗时毫秒={}",
                ragEnabled, toolName, userId, resumeScope, resultCount, durationMs);
        AiTracePublisher tracePublisher = tracePublisherProvider.getIfAvailable();
        if (tracePublisher != null) {
            tracePublisher.tool(new AiToolExecutionEvent(
                    UUID.randomUUID().toString(), "resume-rag", null, "JD_ALIGNMENT", toolName,
                    abbreviate(query, 1000), "results=" + resultCount, true, durationMs, 0, null, Instant.now(),
                    Map.of("指标名称", "RAG召回完成", "RAG开关", ragEnabled, "用户编号", userId == null ? 0L : userId,
                            "简历范围数量", resumeScope, "结果数量", resultCount, "召回耗时毫秒", durationMs)
            ));
        }
    }

    private String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }

    private String digest(String value) {
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }

    private int parseInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private record RetrievedChunk(
            String text,
            String resumeId,
            String chunkType,
            int chunkIndex,
            double rrfScore
    ) {
        RetrievedChunk withRrfScore(double newScore) {
            return new RetrievedChunk(text, resumeId, chunkType, chunkIndex, newScore);
        }
    }
}
