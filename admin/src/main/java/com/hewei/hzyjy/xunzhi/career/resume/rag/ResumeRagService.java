package com.hewei.hzyjy.xunzhi.career.resume.rag;

import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import com.hewei.hzyjy.xunzhi.career.ai.EmbeddingGateway;
import com.hewei.hzyjy.xunzhi.career.config.CareerRagProperties;
import com.hewei.hzyjy.xunzhi.career.observability.AiToolExecutionEvent;
import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
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

    public boolean enabled() {
        return ragProperties.isEnabled();
    }

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
            log.warn("{}; storing text-only chunks for BM25 fallback. expected={}, actual={}",
                    message,
                    chunks.size(), vectors == null ? 0 : vectors.size());
            publishRagToolEvent("rag-embedding-store", "RESUME_ANALYSIS", "storeCvBO",
                    "expected=" + chunks.size(), false, message, Map.of("expected", chunks.size(), "actual", vectors == null ? 0 : vectors.size()));
        } catch (Exception ex) {
            log.warn("Resume embedding failed during chunk storage, storing text-only chunks for BM25 fallback", ex);
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
        if (query == null || query.isBlank()) {
            return List.of();
        }
        long start = System.currentTimeMillis();
        if (!enabled) {
            publishRecallMetric(query, userId, allowedResumeIds == null ? 0 : allowedResumeIds.size(), 0, start,
                    "resume-template-retrieval", false);
            return List.of();
        }
        Set<String> resumeScope = allowedResumeIds == null ? Set.of() : allowedResumeIds;
        Map<String, String> metadataFilters = userId == null ? Map.of() : Map.of(META_USER_ID, String.valueOf(userId));
        List<String> allQueries = new ArrayList<>();
        allQueries.add(query);
        String hyde = generateHyDE(query);
        if (hyde != null && !hyde.isBlank()) {
            allQueries.add(hyde);
        }
        allQueries.addAll(generateMultiQueries(query));

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
        log.info("Career resume RAG completed. querySize={}, userId={}, scope={}, candidates={}, results={}, costMs={}",
                query.length(), userId, resumeScope.size(), candidateResumeIds.size(), results.size(), System.currentTimeMillis() - start);
        publishRecallMetric(query, userId, resumeScope.size(), results.size(), start, "resume-template-retrieval", true);
        return results;
    }

    /**
     * Runs the same scoped hybrid recall as template retrieval but keeps resume identity so callers can
     * compare selected resume versions. No resume outside {@code allowedResumeIds} is returned.
     */
    public List<ResumeRagMatch> retrieveResumeMatches(String query, int limit, Long userId, Set<String> allowedResumeIds) {
        return retrieveResumeMatches(query, limit, userId, allowedResumeIds, ragProperties.isEnabled());
    }

    public List<ResumeRagMatch> retrieveResumeMatches(String query, int limit, Long userId, Set<String> allowedResumeIds, boolean enabled) {
        if (query == null || query.isBlank() || allowedResumeIds == null || allowedResumeIds.isEmpty()) {
            return List.of();
        }
        long start = System.currentTimeMillis();
        if (!enabled) {
            publishRecallMetric(query, userId, allowedResumeIds.size(), 0, start, "resume-job-match-retrieval", false);
            return List.of();
        }
        Set<String> resumeScope = new LinkedHashSet<>(allowedResumeIds);
        Map<String, String> metadataFilters = userId == null ? Map.of() : Map.of(META_USER_ID, String.valueOf(userId));
        List<String> allQueries = new ArrayList<>();
        allQueries.add(query);
        String hyde = generateHyDE(query);
        if (hyde != null && !hyde.isBlank()) {
            allQueries.add(hyde);
        }
        allQueries.addAll(generateMultiQueries(query));
        Set<String> candidateResumeIds = hierarchicalCoarseSearch(allQueries, ragProperties.getCoarseRecallLimit(), resumeScope, metadataFilters);
        if (candidateResumeIds.isEmpty()) {
            candidateResumeIds = resumeScope;
        }
        List<RetrievedChunk> chunks = hybridFineSearch(
                allQueries,
                candidateResumeIds,
                metadataFilters,
                Math.max(limit, 1) * ragProperties.getFineRecallMultiplier());
        Map<String, List<RetrievedChunk>> byResume = chunks.stream()
                .collect(Collectors.groupingBy(RetrievedChunk::resumeId, LinkedHashMap::new, Collectors.toList()));
        List<ResumeRagMatch> matches = resumeScope.stream()
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
                .limit(Math.max(1, limit))
                .toList();
        publishRecallMetric(query, userId, resumeScope.size(), matches.size(), start, "resume-job-match-retrieval", true);
        return matches;
    }

    private String generateHyDE(String query) {
        String cacheKey = CACHE_KEY_HYDE + digest(query);
        String cached = getCached(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            String result = aiGateway.chat(AiPromptRequest.builder()
                    .sceneCode("RESUME_RAG_HYDE")
                    .systemPrompt("Generate a concise hypothetical resume summary and skill list matching the job description.")
                    .userPrompt(query)
                    .build()).content();
            setCached(cacheKey, result);
            return result;
        } catch (Exception ex) {
            log.warn("HyDE generation failed, original query will be used", ex);
            publishRagToolEvent("rag-hyde", "JD_ALIGNMENT", query,
                    "fallback=original-query", false, ex.getMessage(), Map.of("queryDigest", digest(query)));
            return null;
        }
    }

    private List<String> generateMultiQueries(String query) {
        String cacheKey = CACHE_KEY_MQ + digest(query);
        String cached = getCached(cacheKey);
        if (cached != null) {
            return cached.lines().filter(line -> !line.isBlank()).limit(ragProperties.getMultiQueryCount()).toList();
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
            return queries;
        } catch (Exception ex) {
            log.warn("Multi-query generation failed", ex);
            publishRagToolEvent("rag-multi-query", "JD_ALIGNMENT", query,
                    "fallback=empty", false, ex.getMessage(), Map.of("queryDigest", digest(query)));
            return List.of();
        }
    }

    private Set<String> hierarchicalCoarseSearch(List<String> queries, int targetLimit, Set<String> resumeScope, Map<String, String> metadataFilters) {
        Set<String> resumeIds = new LinkedHashSet<>();
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
                log.warn("Vector coarse recall failed, BM25 fine recall can still run. queryDigest={}", digest(query), ex);
                publishRagToolEvent("rag-vector-coarse", "JD_ALIGNMENT", query,
                        "fallback=bm25-fine", false, ex.getMessage(), Map.of("queryDigest", digest(query), "targetLimit", targetLimit));
                continue;
            }
            for (ResumeVectorMatch match : matches) {
                String resumeId = match.document().metadata().get(META_RESUME_ID);
                if (resumeId != null && !resumeId.isBlank()) {
                    resumeIds.add(resumeId);
                    if (resumeIds.size() >= targetLimit) {
                        return resumeIds;
                    }
                }
            }
        }
        return resumeIds;
    }

    private List<RetrievedChunk> hybridFineSearch(List<String> queries, Set<String> candidateResumeIds, Map<String, String> metadataFilters, int limit) {
        List<List<RetrievedChunk>> rankedLists = new ArrayList<>();
        for (String query : queries) {
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
                if (!vectorRanked.isEmpty()) {
                    rankedLists.add(vectorRanked);
                }
            } catch (Exception ex) {
                log.warn("Vector fine recall failed, continuing with independent BM25 lane. queryDigest={}", digest(query), ex);
                publishRagToolEvent("rag-vector-fine", "JD_ALIGNMENT", query,
                        "fallback=bm25", false, ex.getMessage(), Map.of("queryDigest", digest(query), "limit", limit));
            }
            List<RetrievedChunk> bm25Ranked = bm25Recall(query, candidateResumeIds, metadataFilters, limit);
            if (!bm25Ranked.isEmpty()) {
                rankedLists.add(bm25Ranked);
            }
        }
        return rrfFusionChunks(rankedLists, ragProperties.getRrfK(), limit);
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
            log.warn("BM25 recall source lookup failed. queryDigest={}", digest(query), ex);
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
                scores.merge(key, 1.0 / (k + i + 1), Double::sum);
                chunks.putIfAbsent(key, chunk);
            }
        }
        return scores.entrySet().stream()
                .map(entry -> chunks.get(entry.getKey()).withRrfScore(entry.getValue()))
                .sorted(Comparator.comparingDouble(RetrievedChunk::rrfScore).reversed())
                .limit(limit)
                .toList();
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
        if (candidates.isEmpty()) {
            return List.of();
        }
        try {
            List<String> reranked = rerankGateway.rerank(query, candidates, limit);
            if (reranked != null && !reranked.isEmpty()) {
                return reranked;
            }
        } catch (Exception ex) {
            log.warn("External rerank failed, BM25 fallback will be used", ex);
            publishRagToolEvent("rag-rerank", "JD_ALIGNMENT", query,
                    "fallback=bm25", false, ex.getMessage(), Map.of("candidateCount", candidates.size(), "limit", limit));
        }
        Map<String, Double> bm25 = Bm25Scorer.score(candidates, query);
        return candidates.stream()
                .sorted(Comparator.comparingDouble((String text) -> bm25.getOrDefault(text, 0.0)).reversed())
                .limit(limit)
                .toList();
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
            log.debug("RAG cache read failed. key={}", key, ex);
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
            log.debug("RAG cache write failed. key={}", key, ex);
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
