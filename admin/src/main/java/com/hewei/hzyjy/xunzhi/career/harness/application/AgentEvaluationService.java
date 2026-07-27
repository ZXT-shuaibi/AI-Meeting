package com.hewei.hzyjy.xunzhi.career.harness.application;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentEvaluationBaselineDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentEvaluationBaselineMapper;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentRunMapper;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagExperimentDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagExperimentMapper;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Harness 第二期评测服务。
 * 基线只复制既有 RAG 实验完成时的快照，不修改实验、标签或检索结果，因此每次比较均可复盘。
 */
@Service
@RequiredArgsConstructor
public class AgentEvaluationService {
    private static final List<String> METRICS = List.of("recallAtK", "precisionAtK", "mrr", "ndcgAtK", "strongRecallAtK", "totalDurationMs", "fallbackStageCount");
    private final RagExperimentMapper experimentMapper;
    private final AgentEvaluationBaselineMapper baselineMapper;
    private final AgentRunMapper runMapper;

    public AgentEvaluationBaselineDO createRagBaseline(String name, Long experimentId) {
        RagExperimentDO experiment = requireCompletedExperiment(experimentId);
        AgentEvaluationBaselineDO baseline = new AgentEvaluationBaselineDO();
        baseline.setOwnerUserId(experiment.getOwnerUserId());
        baseline.setName(normalizeName(name));
        baseline.setSceneCode("RAG_EXPERIMENT");
        baseline.setSourceExperimentId(experiment.getId());
        baseline.setConfigFingerprint(experiment.getConfigFingerprint());
        baseline.setRuntimeConfigJson(experiment.getRuntimeConfigJson());
        baseline.setMetricSnapshotJson(experiment.getMetricSnapshotJson());
        baselineMapper.insert(baseline);
        return baseline;
    }

    public Map<String, Object> compareRagExperiment(Long baselineId, Long experimentId) {
        AgentEvaluationBaselineDO baseline = baselineMapper.selectById(baselineId);
        if (baseline == null || !"RAG_EXPERIMENT".equals(baseline.getSceneCode())) throw new ClientException("RAG 评测基线不存在或已删除");
        RagExperimentDO current = requireCompletedExperiment(experimentId);
        if (!Objects.equals(baseline.getOwnerUserId(), current.getOwnerUserId())) throw new ClientException("只能比较同一用户实验空间内的 RAG 实验");
        Map<String, Object> before = jsonMap(baseline.getMetricSnapshotJson());
        Map<String, Object> after = jsonMap(current.getMetricSnapshotJson());
        Map<String, Double> deltas = new LinkedHashMap<>();
        for (String metric : METRICS) deltas.put(metric, number(after.get(metric)) - number(before.get(metric)));
        return Map.of("baseline", baseline, "experiment", current, "baselineMetrics", before, "experimentMetrics", after,
                "metricDeltas", deltas, "configChanged", !Objects.equals(baseline.getConfigFingerprint(), current.getConfigFingerprint()));
    }

    /** 管理端按创建时间查看全部冻结基线；基线不随源实验后续标签修改而变化。 */
    public List<AgentEvaluationBaselineDO> listBaselines() {
        return baselineMapper.selectList(Wrappers.<AgentEvaluationBaselineDO>lambdaQuery()
                .orderByDesc(AgentEvaluationBaselineDO::getCreateTime));
    }

    /** 按业务场景汇总已结束运行，作为 Agent 稳定性与成本评测的统一入口。 */
    public List<Map<String, Object>> sceneQuality() {
        return runMapper.selectList(Wrappers.<AgentRunDO>lambdaQuery().in(AgentRunDO::getStatus, List.of("SUCCEEDED", "FAILED", "TIMED_OUT", "CANCELLED")))
                .stream().collect(Collectors.groupingBy(AgentRunDO::getSceneCode, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream().map(entry -> qualityRow(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("sceneCode")))).toList();
    }

    private Map<String, Object> qualityRow(String sceneCode, List<AgentRunDO> runs) {
        long success = runs.stream().filter(run -> "SUCCEEDED".equals(run.getStatus())).count();
        List<Long> durations = runs.stream().map(AgentRunDO::getDurationMs).filter(Objects::nonNull).sorted().toList();
        long ragCount = runs.stream().filter(run -> Boolean.TRUE.equals(run.getRagEnabled())).count();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sceneCode", sceneCode == null ? "UNKNOWN" : sceneCode); row.put("total", runs.size()); row.put("success", success);
        row.put("failed", runs.size() - success); row.put("successRate", runs.isEmpty() ? 0D : success * 100D / runs.size());
        row.put("ragUsageRate", runs.isEmpty() ? 0D : ragCount * 100D / runs.size());
        row.put("averageDurationMs", durations.isEmpty() ? 0D : durations.stream().mapToLong(Long::longValue).average().orElse(0D));
        row.put("p95DurationMs", percentile(durations, .95D)); return row;
    }
    private RagExperimentDO requireCompletedExperiment(Long id) { RagExperimentDO item = experimentMapper.selectById(id); if (item == null || !"COMPLETED".equals(item.getStatus())) throw new ClientException("只能将已完成的 RAG 实验固化为评测基线或参与对比"); return item; }
    private String normalizeName(String value) { if (value == null || value.isBlank()) throw new ClientException("评测基线名称不能为空"); return value.trim().substring(0, Math.min(100, value.trim().length())); }
    @SuppressWarnings("unchecked") private Map<String, Object> jsonMap(String value) { try { Map<String, Object> result = JSON.parseObject(value, Map.class); return result == null ? Map.of() : result; } catch (Exception ignored) { return Map.of(); } }
    private double number(Object value) { return value instanceof Number number ? number.doubleValue() : 0D; }
    private long percentile(List<Long> sorted, double p) { return sorted.isEmpty() ? 0L : sorted.get(Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(sorted.size() * p) - 1))); }
}
