package com.hewei.hzyjy.xunzhi.career.observability;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.observability.dao.entity.AiInvocationTraceDO;
import com.hewei.hzyjy.xunzhi.career.observability.dao.entity.AiToolExecutionDO;
import com.hewei.hzyjy.xunzhi.career.observability.dao.mapper.AiInvocationTraceMapper;
import com.hewei.hzyjy.xunzhi.career.observability.dao.mapper.AiToolExecutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiMonitoringService {

    private static final String RAG_STAGE_PREFIX = "RAG_STAGE_";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_ERROR_SUMMARY_LENGTH = 240;
    private static final Set<String> SAFE_STAGE_METRIC_KEYS = Set.of(
            "inputCount", "outputCount", "candidateCount", "resultCount", "queryCount",
            "multiQueryCount", "cacheHit", "fallbackReason", "rerankEnabled", "degraded"
    );
    private static final Map<String, String> RAG_STAGE_DISPLAY_NAMES = Map.of(
            "QUERY_EXPANSION", "多查询生成",
            "HYDE", "HyDE 假设文档",
            "VECTOR_RETRIEVAL", "向量召回",
            "BM25_RETRIEVAL", "BM25 关键词召回",
            "RRF_FUSION", "RRF 融合",
            "RERANK", "Rerank 重排"
    );

    private final AiInvocationTraceMapper invocationTraceMapper;
    private final AiToolExecutionMapper toolExecutionMapper;

    public Map<String, Object> overview(int minutes) {
        Instant from = Instant.now().minus(Duration.ofMinutes(normalizeMinutes(minutes)));
        List<AiInvocationTraceDO> traces = invocationTraceMapper.selectList(Wrappers.<AiInvocationTraceDO>lambdaQuery()
                .ge(AiInvocationTraceDO::getCreateTime, Date.from(from))
                .in(AiInvocationTraceDO::getEventType, List.of("END", "ERROR"))
                .orderByAsc(AiInvocationTraceDO::getCreateTime));
        List<AiToolExecutionDO> ragStages = loadRagStages(from);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowMinutes", normalizeMinutes(minutes));
        result.put("generatedAt", Instant.now().toString());
        result.put("invocations", invocationSummary(traces, normalizeMinutes(minutes)));
        result.put("scenes", sceneSummary(traces));
        result.put("providers", providerSummary(traces));
        result.put("rag", ragSummary(ragStages));
        result.put("ragCoverage", ragCoverage(traces, ragStages));
        result.put("cost", Map.of(
                "status", "TOKEN_USAGE_NOT_CONNECTED",
                "message", "当前供应商调用链未统一返回 usage，暂不展示估算成本。"
        ));
        result.put("offlineEvaluation", Map.of(
                "status", "PENDING",
                "message", "Recall@K、Precision@K、MRR、NDCG 需由离线评测任务写入后展示。"
        ));
        return result;
    }

    public Map<String, Object> ragStages(int minutes) {
        Instant from = Instant.now().minus(Duration.ofMinutes(normalizeMinutes(minutes)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowMinutes", normalizeMinutes(minutes));
        result.put("generatedAt", Instant.now().toString());
        result.put("stages", ragSummary(loadRagStages(from)));
        return result;
    }

    /**
     * 分页返回已结束调用的安全摘要。明细不返回提示词、简历/JD 原文和工具原始输入输出，
     * 只用于定位业务场景、模型、耗时与 RAG 是否发生降级。
     */
    public Map<String, Object> invocations(
            int minutes,
            int page,
            int size,
            String sceneCode,
            String status,
            String traceId,
            Boolean hasRag) {
        int normalizedMinutes = normalizeMinutes(minutes);
        Instant from = Instant.now().minus(Duration.ofMinutes(normalizedMinutes));
        List<AiInvocationTraceDO> traces = invocationTraceMapper.selectList(Wrappers.<AiInvocationTraceDO>lambdaQuery()
                        .ge(AiInvocationTraceDO::getCreateTime, Date.from(from))
                        .in(AiInvocationTraceDO::getEventType, List.of("END", "ERROR"))
                        .orderByDesc(AiInvocationTraceDO::getCreateTime))
                .stream()
                .filter(trace -> sceneCode == null || sceneCode.isBlank() || sceneCode.equalsIgnoreCase(trace.getSceneCode()))
                .filter(trace -> matchesStatus(trace, status))
                .filter(trace -> traceId == null || traceId.isBlank()
                        || (trace.getTraceId() != null && trace.getTraceId().contains(traceId.trim())))
                .toList();
        Map<String, List<AiToolExecutionDO>> stagesByTrace = loadRagStagesByTraceIds(traces.stream()
                        .map(AiInvocationTraceDO::getTraceId).toList())
                .stream().filter(stage -> stage.getTraceId() != null)
                .collect(Collectors.groupingBy(AiToolExecutionDO::getTraceId, LinkedHashMap::new, Collectors.toList()));
        List<AiInvocationTraceDO> filtered = traces.stream()
                .filter(trace -> hasRag == null || hasRag.equals(stagesByTrace.containsKey(trace.getTraceId())))
                .toList();
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        int fromIndex = Math.min((normalizedPage - 1) * normalizedSize, filtered.size());
        int toIndex = Math.min(fromIndex + normalizedSize, filtered.size());
        long ragTraceCount = filtered.stream().filter(trace -> stagesByTrace.containsKey(trace.getTraceId())).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowMinutes", normalizedMinutes);
        result.put("generatedAt", Instant.now().toString());
        result.put("page", normalizedPage);
        result.put("size", normalizedSize);
        result.put("total", (long) filtered.size());
        result.put("ragTraceCount", ragTraceCount);
        result.put("ragCoverageRate", filtered.isEmpty() ? 0D : ragTraceCount * 100D / filtered.size());
        result.put("items", filtered.subList(fromIndex, toIndex).stream()
                .map(trace -> invocationRow(trace, stagesByTrace.getOrDefault(trace.getTraceId(), List.of())))
                .toList());
        return result;
    }

    /** 返回单次调用和关联 RAG 阶段时间线；trace 不存在时返回空对象。 */
    public Map<String, Object> invocationDetail(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return Map.of();
        }
        AiInvocationTraceDO trace = invocationTraceMapper.selectList(Wrappers.<AiInvocationTraceDO>lambdaQuery()
                        .eq(AiInvocationTraceDO::getTraceId, traceId)
                        .in(AiInvocationTraceDO::getEventType, List.of("END", "ERROR"))
                        .orderByDesc(AiInvocationTraceDO::getCreateTime))
                .stream().findFirst().orElse(null);
        if (trace == null) {
            return Map.of();
        }
        List<AiToolExecutionDO> stages = loadRagStagesByTraceIds(List.of(trace.getTraceId()));
        Map<String, Object> result = invocationRow(trace, stages);
        result.put("stages", stages.stream()
                .sorted(Comparator.comparing(AiToolExecutionDO::getInvocationOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(AiToolExecutionDO::getEventTime, Comparator.nullsLast(Date::compareTo)))
                .map(this::stageRow)
                .toList());
        return result;
    }

    private List<AiToolExecutionDO> loadRagStages(Instant from) {
        return toolExecutionMapper.selectList(Wrappers.<AiToolExecutionDO>lambdaQuery()
                .ge(AiToolExecutionDO::getEventTime, Date.from(from))
                .likeRight(AiToolExecutionDO::getToolName, RAG_STAGE_PREFIX)
                .orderByAsc(AiToolExecutionDO::getEventTime));
    }

    private List<AiToolExecutionDO> loadRagStagesByTraceIds(List<String> traceIds) {
        List<String> validTraceIds = traceIds.stream().filter(Objects::nonNull).filter(id -> !id.isBlank()).toList();
        if (validTraceIds.isEmpty()) {
            return List.of();
        }
        return toolExecutionMapper.selectList(Wrappers.<AiToolExecutionDO>lambdaQuery()
                .in(AiToolExecutionDO::getTraceId, validTraceIds)
                .likeRight(AiToolExecutionDO::getToolName, RAG_STAGE_PREFIX)
                .orderByAsc(AiToolExecutionDO::getEventTime));
    }

    private Map<String, Object> invocationRow(AiInvocationTraceDO trace, List<AiToolExecutionDO> stages) {
        long ragDurationMs = stages.stream().map(AiToolExecutionDO::getExecutionTimeMs)
                .filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        long ragFallbackCount = stages.stream().filter(stage -> !Boolean.TRUE.equals(stage.getSuccess())).count();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("traceId", trace.getTraceId());
        row.put("time", toIsoTime(trace.getEndTime() != null ? trace.getEndTime() : trace.getCreateTime()));
        row.put("sessionId", shorten(trace.getSessionId(), 16));
        row.put("agentId", trace.getAgentId());
        row.put("agentName", valueOrDefault(trace.getAgentName(), valueOrDefault(trace.getSceneCode(), "未命名业务服务")));
        row.put("sceneCode", valueOrDefault(trace.getSceneCode(), "UNKNOWN"));
        row.put("stage", trace.getStage());
        row.put("provider", valueOrDefault(trace.getProvider(), "UNKNOWN"));
        row.put("modelOrFlowId", valueOrDefault(trace.getModelOrFlowId(), "DEFAULT"));
        row.put("status", isSuccessful(trace) ? "SUCCESS" : "FAILED");
        row.put("durationMs", valueOrZero(trace.getDurationMs()));
        row.put("stream", Boolean.TRUE.equals(trace.getStream()));
        row.put("errorSummary", shorten(trace.getErrorMessage(), MAX_ERROR_SUMMARY_LENGTH));
        row.put("hasRag", !stages.isEmpty());
        row.put("ragCalls", stages.size());
        row.put("ragDurationMs", ragDurationMs);
        row.put("ragFallbackCount", ragFallbackCount);
        return row;
    }

    private Map<String, Object> stageRow(AiToolExecutionDO stage) {
        String stageName = stageName(stage.getToolName());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stage", stageName);
        row.put("displayName", displayStageName(stageName));
        row.put("order", stage.getInvocationOrder());
        row.put("time", toIsoTime(stage.getEventTime()));
        row.put("success", Boolean.TRUE.equals(stage.getSuccess()));
        row.put("status", Boolean.TRUE.equals(stage.getSuccess()) ? "SUCCESS" : "DEGRADED");
        row.put("durationMs", valueOrZero(stage.getExecutionTimeMs()));
        row.put("errorSummary", shorten(stage.getErrorMessage(), MAX_ERROR_SUMMARY_LENGTH));
        row.put("metrics", safeStageMetrics(stage.getMetadataJson()));
        return row;
    }

    private Map<String, Object> safeStageMetrics(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            JSONObject metadata = JSON.parseObject(metadataJson);
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : SAFE_STAGE_METRIC_KEYS) {
                Object value = metadata.get(key);
                if (value instanceof Number || value instanceof Boolean) {
                    result.put(key, value);
                } else if (value instanceof String stringValue && !stringValue.isBlank()) {
                    result.put(key, shorten(stringValue, 120));
                }
            }
            return result;
        } catch (Exception ignored) {
            // 历史 metadata 可能不规范；监测页不能因旧记录异常而不可用。
            return Map.of();
        }
    }

    private boolean matchesStatus(AiInvocationTraceDO trace, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        return switch (status.trim().toUpperCase()) {
            case "SUCCESS", "END" -> isSuccessful(trace);
            case "FAILED", "ERROR" -> !isSuccessful(trace);
            default -> true;
        };
    }

    private boolean isSuccessful(AiInvocationTraceDO trace) {
        return "END".equals(trace.getEventType());
    }

    private String stageName(String toolName) {
        return toolName == null ? "UNKNOWN" : toolName.replaceFirst("^" + RAG_STAGE_PREFIX, "");
    }

    private String displayStageName(String stageName) {
        return RAG_STAGE_DISPLAY_NAMES.getOrDefault(stageName, stageName);
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : Math.max(value, 0L);
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…";
    }

    private String toIsoTime(Date value) {
        return value == null ? null : value.toInstant().toString();
    }

    private Map<String, Object> invocationSummary(List<AiInvocationTraceDO> traces, int windowMinutes) {
        long total = traces.size();
        long succeeded = traces.stream().filter(trace -> "END".equals(trace.getEventType())).count();
        long failed = total - succeeded;
        List<Long> durations = traces.stream().map(AiInvocationTraceDO::getDurationMs)
                .filter(Objects::nonNull).filter(duration -> duration >= 0).toList();
        Map<String, Object> result = latencySummary(durations);
        result.put("total", total);
        result.put("success", succeeded);
        result.put("failed", failed);
        result.put("successRate", total == 0 ? 0D : succeeded * 100D / total);
        result.put("failureRate", total == 0 ? 0D : failed * 100D / total);
        result.put("throughputPerMinute", total * 1D / windowMinutes);
        return result;
    }

    private List<Map<String, Object>> sceneSummary(List<AiInvocationTraceDO> traces) {
        return traces.stream().collect(Collectors.groupingBy(
                        trace -> trace.getSceneCode() == null ? "UNKNOWN" : trace.getSceneCode(),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    List<AiInvocationTraceDO> group = entry.getValue();
                    long success = group.stream().filter(trace -> "END".equals(trace.getEventType())).count();
                    Map<String, Object> row = latencySummary(group.stream().map(AiInvocationTraceDO::getDurationMs)
                            .filter(Objects::nonNull).toList());
                    row.put("sceneCode", entry.getKey());
                    row.put("total", group.size());
                    row.put("success", success);
                    row.put("failed", group.size() - success);
                    row.put("successRate", group.isEmpty() ? 0D : success * 100D / group.size());
                    row.put("failureRate", group.isEmpty() ? 0D : (group.size() - success) * 100D / group.size());
                    return row;
                })
                .sorted(Comparator.comparing(row -> String.valueOf(row.get("sceneCode"))))
                .toList();
    }

    private List<Map<String, Object>> ragSummary(List<AiToolExecutionDO> stages) {
        return stages.stream().collect(Collectors.groupingBy(
                        stage -> stage.getToolName().substring(RAG_STAGE_PREFIX.length()),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    List<AiToolExecutionDO> group = entry.getValue();
                    long fallbackCount = group.stream().filter(stage -> !Boolean.TRUE.equals(stage.getSuccess())).count();
                    Map<String, Object> row = latencySummary(group.stream().map(AiToolExecutionDO::getExecutionTimeMs)
                            .filter(Objects::nonNull).toList());
                    row.put("stage", entry.getKey());
                    row.put("displayName", displayStageName(entry.getKey()));
                    row.put("calls", group.size());
                    row.put("success", group.size() - fallbackCount);
                    row.put("fallbackCount", fallbackCount);
                    row.put("fallbackRate", group.isEmpty() ? 0D : fallbackCount * 100D / group.size());
                    row.put("averageInputCount", averageMetadataNumber(group, "inputCount"));
                    row.put("averageOutputCount", averageMetadataNumber(group, "outputCount"));
                    return row;
                })
                .sorted(Comparator.comparing(row -> String.valueOf(row.get("stage"))))
                .toList();
    }

    private List<Map<String, Object>> providerSummary(List<AiInvocationTraceDO> traces) {
        return traces.stream().collect(Collectors.groupingBy(
                        trace -> valueOrDefault(trace.getProvider(), "UNKNOWN") + " / "
                                + valueOrDefault(trace.getModelOrFlowId(), "DEFAULT"),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream().map(entry -> {
                    List<AiInvocationTraceDO> group = entry.getValue();
                    long success = group.stream().filter(this::isSuccessful).count();
                    Map<String, Object> row = latencySummary(group.stream().map(AiInvocationTraceDO::getDurationMs)
                            .filter(Objects::nonNull).filter(duration -> duration >= 0).toList());
                    row.put("providerModel", entry.getKey());
                    row.put("total", group.size());
                    row.put("success", success);
                    row.put("failed", group.size() - success);
                    row.put("successRate", group.isEmpty() ? 0D : success * 100D / group.size());
                    row.put("failureRate", group.isEmpty() ? 0D : (group.size() - success) * 100D / group.size());
                    return row;
                })
                .sorted(Comparator.comparing(row -> String.valueOf(row.get("providerModel"))))
                .toList();
    }

    private Map<String, Object> ragCoverage(List<AiInvocationTraceDO> traces, List<AiToolExecutionDO> stages) {
        Set<String> allInvocationTraceIds = traces.stream().map(AiInvocationTraceDO::getTraceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> stageTraceIds = stages.stream().map(AiToolExecutionDO::getTraceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> businessEligibleTraceIds = traces.stream().filter(this::isRagRequestedBusinessCall)
                .map(AiInvocationTraceDO::getTraceId).filter(Objects::nonNull).collect(Collectors.toSet());
        long globalParticipationCount = stageTraceIds.stream().filter(allInvocationTraceIds::contains).count();
        long businessCoveredCount = stageTraceIds.stream().filter(businessEligibleTraceIds::contains).count();
        long fallbackCount = stages.stream().filter(stage -> !Boolean.TRUE.equals(stage.getSuccess())).count();
        long totalDurationMs = stages.stream().map(AiToolExecutionDO::getExecutionTimeMs)
                .filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        // 兼容旧前端字段：覆盖率现在明确表示“应使用 RAG 的业务调用”是否真正留下了 RAG 阶段。
        result.put("traceCount", businessCoveredCount);
        result.put("coverageRate", businessEligibleTraceIds.isEmpty() ? 0D : businessCoveredCount * 100D / businessEligibleTraceIds.size());
        result.put("businessEligibleCount", (long) businessEligibleTraceIds.size());
        result.put("businessCoveredCount", businessCoveredCount);
        result.put("businessCoverageRate", businessEligibleTraceIds.isEmpty() ? 0D : businessCoveredCount * 100D / businessEligibleTraceIds.size());
        result.put("globalParticipationCount", globalParticipationCount);
        result.put("globalParticipationRate", traces.isEmpty() ? 0D : globalParticipationCount * 100D / traces.size());
        result.put("stageCalls", stages.size());
        result.put("fallbackCount", fallbackCount);
        result.put("fallbackRate", stages.isEmpty() ? 0D : fallbackCount * 100D / stages.size());
        result.put("totalDurationMs", totalDurationMs);
        return result;
    }

    /** 只以主业务 Trace 上明确写入的 ragRequested 标记作为业务覆盖率分母，避免 TTS/OCR 等污染。 */
    private boolean isRagRequestedBusinessCall(AiInvocationTraceDO trace) {
        if (trace.getMetadataJson() == null || trace.getMetadataJson().isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(JSON.parseObject(trace.getMetadataJson()).getBoolean("ragRequested"));
        } catch (Exception ignored) {
            // 历史数据 metadata 不规范时不将其误判为 RAG 业务调用。
            return false;
        }
    }

    private double averageMetadataNumber(List<AiToolExecutionDO> stages, String key) {
        return stages.stream().map(stage -> safeStageMetrics(stage.getMetadataJson()).get(key))
                .filter(Number.class::isInstance).map(Number.class::cast)
                .mapToDouble(Number::doubleValue).average().orElse(0D);
    }

    private Map<String, Object> latencySummary(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("averageLatencyMs", sorted.isEmpty() ? 0D : sorted.stream().mapToLong(Long::longValue).average().orElse(0D));
        result.put("p50LatencyMs", percentile(sorted, 0.50));
        result.put("p95LatencyMs", percentile(sorted, 0.95));
        result.put("p99LatencyMs", percentile(sorted, 0.99));
        return result;
    }

    private long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1));
        return sorted.get(index);
    }

    private int normalizeMinutes(int minutes) {
        return Math.min(10_080, Math.max(1, minutes));
    }
}
