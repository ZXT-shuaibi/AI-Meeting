package com.hewei.hzyjy.xunzhi.career.harness.application;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunEventDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentRunEventMapper;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Agent 运行中心的只读查询服务。
 *
 * <p>该服务只返回管理端排障与实验复盘所需的摘要字段，不返回 JD、简历、面试回答、提示词和工具原始入出参。
 * 运行主账仍由 {@link AgentRunCoordinator} 写入；这里不修改任务状态，也不充当任何业务模块的事实源。</p>
 */
@Service
@RequiredArgsConstructor
public class AgentRunQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_TEXT_LENGTH = 240;
    private static final Set<String> SAFE_EVENT_METADATA_KEYS = Set.of(
            "resultCount", "candidateCount", "queryCount", "iterations", "score", "scorePassed",
            "durationMs", "costMs", "ragEnabled", "degraded", "fallbackReason", "fallbackStageCount", "retryCount",
            "jdSafetyRiskDetected", "filteredInstructionCount", "structuredFieldsCount", "jdSafetyDecision",
            "riskLevel", "riskSignals", "normalizedInputDigest", "contextFingerprint"
    );

    private final AgentRunMapper agentRunMapper;
    private final AgentRunEventMapper agentRunEventMapper;

    /**
     * 分页查询运行摘要。数据库条件用于缩小查询范围，内存二次过滤用于兼容历史数据中的空字段和大小写差异。
     */
    public Map<String, Object> list(
            int page, int size, String sceneCode, String status, Date fromTime, Date toTime) {
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        List<AgentRunDO> allRuns = agentRunMapper.selectList(Wrappers.<AgentRunDO>lambdaQuery()
                .eq(hasText(sceneCode), AgentRunDO::getSceneCode, trimToNull(sceneCode))
                .eq(hasText(status), AgentRunDO::getStatus, trimToNull(status))
                .ge(fromTime != null, AgentRunDO::getCreateTime, fromTime)
                .le(toTime != null, AgentRunDO::getCreateTime, toTime)
                .orderByDesc(AgentRunDO::getCreateTime));
        List<AgentRunDO> filtered = allRuns.stream()
                .filter(run -> equalsIgnoreCaseOrEmpty(sceneCode, run.getSceneCode()))
                .filter(run -> equalsIgnoreCaseOrEmpty(status, run.getStatus()))
                .filter(run -> inTimeRange(run.getCreateTime(), fromTime, toTime))
                .toList();
        // 页码来自请求参数，必须先用 long 计算偏移量。否则 Integer.MAX_VALUE 等极端页码会溢出为负数，
        // 随后的 subList 会抛出异常；对已经越过总数的页码应稳定返回空列表。
        long requestedOffset = ((long) normalizedPage - 1L) * normalizedSize;
        int fromIndex = requestedOffset >= filtered.size() ? filtered.size() : (int) requestedOffset;
        int toIndex = Math.min(fromIndex + normalizedSize, filtered.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page", normalizedPage);
        result.put("size", normalizedSize);
        result.put("total", (long) filtered.size());
        result.put("items", filtered.subList(fromIndex, toIndex).stream().map(this::runSummary).toList());
        return result;
    }

    /**
     * 查询单次任务详情和按发生时间排序的阶段事件。不存在时返回空对象，便于前端区分“记录已清理”和接口异常。
     */
    public Map<String, Object> detail(String runId) {
        if (!hasText(runId)) {
            return Map.of();
        }
        AgentRunDO run = agentRunMapper.selectList(Wrappers.<AgentRunDO>lambdaQuery()
                        .eq(AgentRunDO::getRunId, runId.trim())
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        if (run == null) {
            return Map.of();
        }
        List<Map<String, Object>> events = agentRunEventMapper.selectList(Wrappers.<AgentRunEventDO>lambdaQuery()
                        .eq(AgentRunEventDO::getRunId, run.getRunId())
                        .orderByAsc(AgentRunEventDO::getOccurredAt))
                .stream()
                .sorted(Comparator.comparing(AgentRunEventDO::getOccurredAt, Comparator.nullsLast(Date::compareTo))
                        .thenComparing(AgentRunEventDO::getId, Comparator.nullsLast(Long::compareTo)))
                .map(this::eventSummary)
                .toList();
        Map<String, Object> detail = new LinkedHashMap<>(runSummary(run));
        detail.put("businessId", nullToEmpty(run.getBusinessId()));
        detail.put("sessionId", shorten(run.getSessionId()));
        detail.put("configFingerprint", shorten(run.getConfigFingerprint()));
        detail.put("resultSummary", shorten(run.getResultSummary()));
        detail.put("errorMessage", shorten(run.getErrorMessage()));
        detail.put("events", events);
        return detail;
    }

    private Map<String, Object> runSummary(AgentRunDO run) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("runId", run.getRunId());
        item.put("traceId", shorten(run.getTraceId()));
        item.put("userId", run.getUserId() == null ? "" : String.valueOf(run.getUserId()));
        item.put("businessType", nullToEmpty(run.getBusinessType()));
        item.put("sceneCode", nullToEmpty(run.getSceneCode()));
        item.put("status", nullToEmpty(run.getStatus()));
        item.put("ragEnabled", Boolean.TRUE.equals(run.getRagEnabled()));
        item.put("startedAt", toIso(run.getStartedAt()));
        item.put("finishedAt", toIso(run.getFinishedAt()));
        item.put("durationMs", durationMs(run));
        item.put("createdAt", toIso(run.getCreateTime()));
        return item;
    }

    private Map<String, Object> eventSummary(AgentRunEventDO event) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("eventType", nullToEmpty(event.getEventType()));
        item.put("stageCode", nullToEmpty(event.getStageCode()));
        item.put("status", nullToEmpty(event.getStatus()));
        item.put("message", shorten(event.getMessage()));
        item.put("occurredAt", toIso(event.getOccurredAt()));
        item.put("metrics", safeEventMetadata(event.getMetadataJson()));
        return item;
    }

    private Map<String, Object> safeEventMetadata(String metadataJson) {
        if (!hasText(metadataJson)) {
            return Map.of();
        }
        try {
            JSONObject metadata = JSON.parseObject(metadataJson);
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : SAFE_EVENT_METADATA_KEYS) {
                Object value = metadata.get(key);
                if (value instanceof Number || value instanceof Boolean) {
                    result.put(key, value);
                } else if (value instanceof String text && hasText(text)) {
                    result.put(key, shorten(text));
                } else if (value instanceof java.util.Collection<?> values && "riskSignals".equals(key)) {
                    // Signal names are fixed enums. Keep only enum-shaped values so free text never enters monitoring output.
                    result.put(key, values.stream()
                            .map(String::valueOf)
                            .filter(item -> item.matches("[A-Z_]{1,64}"))
                            .limit(16)
                            .toList());
                }
            }
            return result;
        } catch (Exception ignored) {
            // 历史事件可能是旧格式或不规范 JSON；监控详情不得因一条旧审计记录失效。
            return Map.of();
        }
    }

    private boolean inTimeRange(Date value, Date fromTime, Date toTime) {
        if (value == null) {
            return fromTime == null && toTime == null;
        }
        return (fromTime == null || !value.before(fromTime)) && (toTime == null || !value.after(toTime));
    }

    private boolean equalsIgnoreCaseOrEmpty(String expected, String actual) {
        return !hasText(expected) || Objects.equals(expected.trim().toUpperCase(), nullToEmpty(actual).toUpperCase());
    }

    private long durationMs(AgentRunDO run) {
        if (run.getDurationMs() != null && run.getDurationMs() >= 0) {
            return run.getDurationMs();
        }
        if (run.getStartedAt() == null || run.getFinishedAt() == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(run.getStartedAt().toInstant(), run.getFinishedAt().toInstant()).toMillis());
    }

    private String toIso(Date value) {
        return value == null ? null : value.toInstant().toString();
    }

    private String shorten(String value) {
        if (!hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_TEXT_LENGTH ? trimmed : trimmed.substring(0, MAX_TEXT_LENGTH) + "…";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
