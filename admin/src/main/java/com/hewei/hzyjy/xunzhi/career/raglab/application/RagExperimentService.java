package com.hewei.hzyjy.xunzhi.career.raglab.application;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.CreateRagExperimentReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.UpdateRagJudgementsReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.UpdateRagEvidenceAnnotationsReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.*;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.*;
import com.hewei.hzyjy.xunzhi.career.raglab.model.RagExperimentMetrics;
import com.hewei.hzyjy.xunzhi.career.raglab.model.RagExperimentRuntimeOptions;
import com.hewei.hzyjy.xunzhi.career.raglab.model.RagRelevanceRule;
import com.hewei.hzyjy.xunzhi.career.harness.application.AgentRunCoordinator;
import com.hewei.hzyjy.xunzhi.career.harness.model.AgentRunStartCommand;
import com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagMatch;
import com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagService;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 实验的生命周期服务。
 *
 * <p>创建阶段冻结数据集、配置和 0/1/3 真值，异步阶段只读取这些快照并保存召回结果。由此即便用户
 * 后续更改标签、全局配置或数据集，也不会改变已经完成实验的评测结论。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagExperimentService {
    private static final String RULE_SOURCE = "SYSTEM_TAG_MATCH_V1";

    private final RagExperimentDatasetService datasetService;
    private final RagExperimentMetricCalculator metricCalculator;
    private final RagExperimentEvidenceMetricCalculator evidenceMetricCalculator;
    private final ResumeRagService resumeRagService;
    private final RagExperimentMapper experimentMapper;
    private final RagExperimentJudgementMapper judgementMapper;
    private final RagExperimentResultMapper resultMapper;
    private final RagResumeTagMapper tagMapper;
    @Qualifier("careerTaskExecutor")
    private final TaskExecutor careerTaskExecutor;

    /**
     * Harness 运行总账是旁路审计能力。保留可选注入可确保尚未执行新增建表脚本的旧环境
     * 仍能正常完成 RAG 实验，不能让观测能力反向阻断真实评测任务。
     */
    @Autowired(required = false)
    private AgentRunCoordinator agentRunCoordinator;

    @Transactional(rollbackFor = Exception.class)
    public RagExperimentDO createAndRun(Long requesterUserId, boolean administrator, CreateRagExperimentReqDTO request) {
        RagExperimentDatasetDO dataset = datasetService.requireDataset(request.getDatasetId());
        Long ownerUserId = dataset.getOwnerUserId();
        if (!administrator && !Objects.equals(requesterUserId, ownerUserId)) {
            throw new ClientException("只能使用自己创建的 RAG 测试集运行实验");
        }
        RagExperimentRuntimeOptions options = request.getRuntimeOptions();
        List<RagRelevanceRule> rules = Optional.ofNullable(request.getRelevanceRules()).orElse(List.of());
        if (rules.isEmpty()) {
            throw new ClientException("至少配置一条标签判定规则");
        }
        validateRules(rules);
        List<RagExperimentDatasetItemDO> items = datasetService.listItems(dataset.getId());
        if (items.isEmpty()) {
            throw new ClientException("测试集内没有可用于实验的已解析简历");
        }
        String configJson = JSON.toJSONString(options);
        String rulesJson = JSON.toJSONString(rules);
        RagExperimentDO experiment = new RagExperimentDO();
        experiment.setOwnerUserId(ownerUserId);
        experiment.setName(request.getName().trim());
        experiment.setDatasetId(dataset.getId());
        experiment.setJobDescription(request.getJobDescription().trim());
        experiment.setTopK(options.topK());
        experiment.setStatus("PENDING");
        experiment.setRuntimeConfigJson(configJson);
        experiment.setJudgementSnapshotJson(rulesJson);
        experiment.setConfigFingerprint(DigestUtils.md5DigestAsHex((configJson + "|" + rulesJson).getBytes(StandardCharsets.UTF_8)));
        experiment.setComparisonGroup(blankToNull(request.getComparisonGroup()));
        experimentMapper.insert(experiment);

        Map<Long, List<RagResumeTagDO>> tagsByResume = loadTags(ownerUserId, items);
        for (RagExperimentDatasetItemDO item : items) {
            int score = calculateAutomaticScore(tagsByResume.getOrDefault(item.getResumeId(), List.of()), rules);
            RagExperimentJudgementDO judgement = new RagExperimentJudgementDO();
            judgement.setExperimentId(experiment.getId());
            judgement.setOwnerUserId(ownerUserId);
            judgement.setResumeId(item.getResumeId());
            judgement.setAutomaticScore(score);
            judgement.setFinalScore(score);
            judgement.setRuleSource(RULE_SOURCE);
            judgement.setRuleSnapshotJson(rulesJson);
            judgementMapper.insert(judgement);
        }
        // 仅在快照和真值均已提交后再入队，避免线程池抢跑读取到未提交实验。
        Long experimentId = experiment.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                careerTaskExecutor.execute(() -> run(experimentId));
            }
        });
        return experiment;
    }

    /** 异步执行必须永远自己记录失败状态，避免前端轮询永久处于“运行中”。 */
    public void run(Long experimentId) {
        RagExperimentDO experiment = requireExperiment(experimentId);
        long startedAt = System.currentTimeMillis();
        String harnessRunId = null;
        try {
            experiment.setStatus("RUNNING");
            experiment.setStartedAt(new Date());
            experiment.setErrorSummary(null);
            experimentMapper.updateById(experiment);

            RagExperimentRuntimeOptions options = JSON.parseObject(experiment.getRuntimeConfigJson(), RagExperimentRuntimeOptions.class);
            List<RagExperimentDatasetItemDO> items = datasetService.listItems(experiment.getDatasetId());
            Set<String> allowedIds = items.stream().map(RagExperimentDatasetItemDO::getResumeId)
                    .map(String::valueOf).collect(Collectors.toCollection(LinkedHashSet::new));
            String traceId = "rag-exp-" + experiment.getId();
            harnessRunId = startHarnessRun(experiment, options, items.size(), traceId);
            stageHarnessRun(harnessRunId, "RAG_RETRIEVE", "开始执行实验检索", Map.of(
                    "candidateCount", allowedIds.size(), "ragEnabled", options.ragEnabled()));
            List<ResumeRagMatch> matches = resumeRagService.retrieveExperimentMatches(
                    experiment.getJobDescription(), experiment.getOwnerUserId(), allowedIds, options,
                    traceId);
            Map<String, Object> stageTrace = resumeRagService.consumeExperimentStageTrace(traceId);
            int fallbackStageCount = (int) numeric(stageTrace.get("fallbackStageCount"));
            stageHarnessRun(harnessRunId, "RAG_RETRIEVE", "实验检索完成", Map.of(
                    "resultCount", matches.size(), "fallbackStageCount", fallbackStageCount));
            saveResults(experiment, matches, startedAt, stageTrace, fallbackStageCount);
            stageHarnessRun(harnessRunId, "PERSIST", "实验召回结果已归档", Map.of("resultCount", matches.size()));
            stageHarnessRun(harnessRunId, "METRIC_CALCULATE", "开始计算检索量化指标", Map.of(
                    "resultCount", matches.size()));
            RagExperimentMetrics metrics = calculateMetrics(experiment.getId(), options.topK());
            experiment.setMetricSnapshotJson(JSON.toJSONString(metricSnapshot(metrics, System.currentTimeMillis() - startedAt, matches.size(), fallbackStageCount)));
            experiment.setStatus("COMPLETED");
            experiment.setCompletedAt(new Date());
            experimentMapper.updateById(experiment);
            stageHarnessRun(harnessRunId, "METRIC_CALCULATE", "检索量化指标计算完成", Map.of(
                    "resultCount", matches.size(), "score", metrics.ndcgAtK()));
            succeedHarnessRun(harnessRunId, "RAG 实验已完成", Map.of(
                    "resultCount", matches.size(), "durationMs", System.currentTimeMillis() - startedAt,
                    "fallbackStageCount", fallbackStageCount));
            log.info("RAG实验完成。实验编号={} 用户编号={} TopK={} Recall@K={} Precision@K={} MRR={} NDCG@K={} 总耗时毫秒={} 降级阶段数={}",
                    experiment.getId(), experiment.getOwnerUserId(), options.topK(), metrics.recallAtK(), metrics.precisionAtK(),
                    metrics.mrr(), metrics.ndcgAtK(), System.currentTimeMillis() - startedAt, 0);
        } catch (Exception ex) {
            experiment.setStatus("FAILED");
            experiment.setCompletedAt(new Date());
            experiment.setErrorSummary(shortError(ex));
            experimentMapper.updateById(experiment);
            failHarnessRun(harnessRunId, shortError(ex), Map.of("durationMs", System.currentTimeMillis() - startedAt));
            log.error("RAG实验执行失败。实验编号={}，用户编号={}，原因={}", experimentId, experiment.getOwnerUserId(), shortError(ex), ex);
        }
    }

    /**
     * 为实验建立一条可复盘运行总账。输入仅记录候选数量和 Top-K，JD 原文仍只保留在既有实验快照表中，
     * 以免运维页面和时间线成为新的敏感数据泄露面。
     */
    private String startHarnessRun(RagExperimentDO experiment, RagExperimentRuntimeOptions options,
                                   int candidateCount, String traceId) {
        if (agentRunCoordinator == null) {
            return null;
        }
        try {
            return agentRunCoordinator.start(new AgentRunStartCommand(
                    "RAG_EXPERIMENT", "RAG_EXPERIMENT", experiment.getOwnerUserId(),
                    String.valueOf(experiment.getId()), "rag-experiment:" + experiment.getId(), traceId,
                    options.ragEnabled(), "数据集编号=" + experiment.getDatasetId() + ";候选简历数=" + candidateCount
                    + ";TopK=" + options.topK(), experiment.getConfigFingerprint()));
        } catch (Exception ex) {
            log.warn("Harness 运行审计创建失败，继续执行 RAG 实验。实验编号={}", experiment.getId(), ex);
            return null;
        }
    }

    private void stageHarnessRun(String runId, String stageCode, String message, Map<String, Object> metadata) {
        if (runId == null || agentRunCoordinator == null) {
            return;
        }
        try {
            agentRunCoordinator.stage(runId, stageCode, message, metadata);
        } catch (Exception ex) {
            log.warn("Harness 阶段审计写入失败，继续执行 RAG 实验。运行编号={} 阶段={}", runId, stageCode, ex);
        }
    }

    private void succeedHarnessRun(String runId, String resultSummary, Map<String, Object> metadata) {
        if (runId == null || agentRunCoordinator == null) {
            return;
        }
        try {
            agentRunCoordinator.succeed(runId, resultSummary, metadata);
        } catch (Exception ex) {
            log.warn("Harness 成功审计写入失败，继续结束 RAG 实验。运行编号={}", runId, ex);
        }
    }

    private void failHarnessRun(String runId, String errorSummary, Map<String, Object> metadata) {
        if (runId == null || agentRunCoordinator == null) {
            return;
        }
        try {
            agentRunCoordinator.fail(runId, errorSummary, metadata);
        } catch (Exception auditEx) {
            log.warn("Harness 失败审计写入失败，保留原始 RAG 实验失败状态。运行编号={}", runId, auditEx);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void overrideJudgements(Long experimentId, Long ownerUserId, List<UpdateRagJudgementsReqDTO.Item> items) {
        RagExperimentDO experiment = requireOwnedExperiment(experimentId, ownerUserId);
        for (UpdateRagJudgementsReqDTO.Item item : Optional.ofNullable(items).orElse(List.of())) {
            if (item.getScore() == null || !isAllowedScore(item.getScore())) {
                throw new ClientException("人工覆写评分只允许 0、1 或 3");
            }
            RagExperimentJudgementDO judgement = judgementMapper.selectOne(Wrappers.lambdaQuery(RagExperimentJudgementDO.class)
                    .eq(RagExperimentJudgementDO::getExperimentId, experimentId)
                    .eq(RagExperimentJudgementDO::getResumeId, item.getResumeId()).last("LIMIT 1"));
            if (judgement == null) {
                throw new ClientException("被覆写的简历不在该实验测试集中");
            }
            judgement.setFinalScore(item.getScore());
            judgement.setRuleSource("MANUAL_OVERRIDE");
            judgement.setOverrideReason(blankToNull(item.getReason()));
            judgementMapper.updateById(judgement);
        }
        RagExperimentMetrics metrics = calculateMetrics(experimentId, experiment.getTopK());
        long duration = experiment.getStartedAt() == null || experiment.getCompletedAt() == null ? 0L
                : Math.max(0L, experiment.getCompletedAt().getTime() - experiment.getStartedAt().getTime());
        int returned = resultMapper.selectCount(Wrappers.lambdaQuery(RagExperimentResultDO.class)
                .eq(RagExperimentResultDO::getExperimentId, experimentId)).intValue();
        int fallbackStageCount = (int) numeric(jsonMap(experiment.getMetricSnapshotJson()).get("fallbackStageCount"));
        experiment.setMetricSnapshotJson(JSON.toJSONString(metricSnapshot(metrics, duration, returned, fallbackStageCount)));
        experimentMapper.updateById(experiment);
        refreshEvidenceMetricSnapshot(experiment);
    }

    /**
     * 将证据核验写回本次实验的结果快照；不影响上传简历的可复用基础标签。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateEvidenceAnnotations(Long experimentId, Long ownerUserId,
                                          List<UpdateRagEvidenceAnnotationsReqDTO.Item> items) {
        RagExperimentDO experiment = requireOwnedExperiment(experimentId, ownerUserId);
        Map<Long, List<UpdateRagEvidenceAnnotationsReqDTO.Item>> annotationsByResume = Optional.ofNullable(items)
                .orElse(List.of()).stream().collect(Collectors.groupingBy(UpdateRagEvidenceAnnotationsReqDTO.Item::getResumeId));
        for (Map.Entry<Long, List<UpdateRagEvidenceAnnotationsReqDTO.Item>> entry : annotationsByResume.entrySet()) {
            RagExperimentResultDO result = resultMapper.selectOne(Wrappers.lambdaQuery(RagExperimentResultDO.class)
                    .eq(RagExperimentResultDO::getExperimentId, experimentId)
                    .eq(RagExperimentResultDO::getResumeId, entry.getKey()).last("LIMIT 1"));
            if (result == null) {
                throw new ClientException("待标注的简历不在本次实验的召回结果中");
            }
            JSONArray evidence = parseEvidence(result.getMatchedChunksJson());
            for (UpdateRagEvidenceAnnotationsReqDTO.Item annotation : entry.getValue()) {
                int index = annotation.getEvidenceIndex();
                if (index < 0 || index >= evidence.size()) {
                    throw new ClientException("证据片段序号无效，请刷新实验结果后重试");
                }
                JSONObject normalized = normalizeEvidence(evidence.get(index));
                normalized.put("hit", annotation.getHit());
                evidence.set(index, normalized);
            }
            result.setMatchedChunksJson(JSON.toJSONString(evidence));
            resultMapper.updateById(result);
        }
        refreshEvidenceMetricSnapshot(experiment);
    }

    public RagExperimentDO requireExperiment(Long experimentId) {
        RagExperimentDO experiment = experimentMapper.selectById(experimentId);
        if (experiment == null) {
            throw new ClientException("RAG 实验不存在或已删除");
        }
        return experiment;
    }

    public RagExperimentDO requireOwnedExperiment(Long experimentId, Long ownerUserId) {
        RagExperimentDO experiment = requireExperiment(experimentId);
        if (!Objects.equals(ownerUserId, experiment.getOwnerUserId())) {
            throw new ClientException("无权访问其他用户的 RAG 实验数据");
        }
        return experiment;
    }

    public Map<String, Object> detail(Long experimentId, Long ownerUserId) {
        RagExperimentDO experiment = requireOwnedExperiment(experimentId, ownerUserId);
        List<RagExperimentJudgementDO> judgements = judgementMapper.selectList(Wrappers.lambdaQuery(RagExperimentJudgementDO.class)
                .eq(RagExperimentJudgementDO::getExperimentId, experimentId));
        List<RagExperimentResultDO> results = resultMapper.selectList(Wrappers.lambdaQuery(RagExperimentResultDO.class)
                .eq(RagExperimentResultDO::getExperimentId, experimentId)
                .orderByAsc(RagExperimentResultDO::getRankNo));
        return Map.of("experiment", experiment, "judgements", judgements, "results", results,
                "runtimeOptions", parseJson(experiment.getRuntimeConfigJson()),
                "relevanceRules", parseJson(experiment.getJudgementSnapshotJson()),
                "metrics", parseJson(experiment.getMetricSnapshotJson()));
    }

    public List<RagExperimentDO> list(Long ownerUserId) {
        return experimentMapper.selectList(Wrappers.lambdaQuery(RagExperimentDO.class)
                .eq(RagExperimentDO::getOwnerUserId, ownerUserId)
                .orderByDesc(RagExperimentDO::getCreateTime));
    }

    /** 管理员全量视图使用；调用方必须已完成管理员身份校验。 */
    public List<RagExperimentDO> listAll() {
        return experimentMapper.selectList(Wrappers.lambdaQuery(RagExperimentDO.class)
                .orderByDesc(RagExperimentDO::getCreateTime));
    }

    public Map<String, Object> compare(Long leftId, Long rightId, Long ownerUserId) {
        RagExperimentDO left = requireOwnedExperiment(leftId, ownerUserId);
        RagExperimentDO right = requireOwnedExperiment(rightId, ownerUserId);
        Map<String, Object> leftMetrics = jsonMap(left.getMetricSnapshotJson());
        Map<String, Object> rightMetrics = jsonMap(right.getMetricSnapshotJson());
        Map<String, Object> deltas = new LinkedHashMap<>();
        for (String key : List.of("recallAtK", "precisionAtK", "mrr", "ndcgAtK", "strongRecallAtK", "totalDurationMs", "fallbackStageCount")) {
            deltas.put(key, numeric(rightMetrics.get(key)) - numeric(leftMetrics.get(key)));
        }
        return Map.of("left", left, "right", right, "leftMetrics", leftMetrics, "rightMetrics", rightMetrics,
                "metricDeltas", deltas, "runtimeConfigChanged", !Objects.equals(left.getRuntimeConfigJson(), right.getRuntimeConfigJson()));
    }

    private void saveResults(RagExperimentDO experiment, List<ResumeRagMatch> matches, long startedAt,
                             Map<String, Object> stageTrace, int fallbackStageCount) {
        resultMapper.delete(Wrappers.lambdaQuery(RagExperimentResultDO.class).eq(RagExperimentResultDO::getExperimentId, experiment.getId()));
        int rank = 0;
        for (ResumeRagMatch match : matches) {
            Long resumeId = parseLong(match.resumeId());
            if (resumeId == null) continue;
            RagExperimentResultDO result = new RagExperimentResultDO();
            result.setExperimentId(experiment.getId());
            result.setOwnerUserId(experiment.getOwnerUserId());
            result.setResumeId(resumeId);
            result.setRankNo(++rank);
            result.setRelevanceScore(java.math.BigDecimal.valueOf(match.score()));
            result.setRagScore(java.math.BigDecimal.valueOf(match.score()));
            result.setMatchedChunksJson(JSON.toJSONString(match.evidence()));
            result.setStageTraceJson(JSON.toJSONString(Map.of(
                    "runtimeOptions", parseJson(experiment.getRuntimeConfigJson()),
                    "trace", stageTrace == null ? Map.of() : stageTrace,
                    "fallbackStageCount", fallbackStageCount)));
            result.setTotalDurationMs(Math.max(0, System.currentTimeMillis() - startedAt));
            result.setFallbackStageCount(fallbackStageCount);
            result.setResultSnapshotJson(JSON.toJSONString(Map.of("resumeId", resumeId, "rankNo", rank, "score", match.score(), "evidence", match.evidence())));
            resultMapper.insert(result);
        }
    }

    private RagExperimentMetrics calculateMetrics(Long experimentId, int topK) {
        Map<Long, Integer> judgements = judgementMapper.selectList(Wrappers.lambdaQuery(RagExperimentJudgementDO.class)
                        .eq(RagExperimentJudgementDO::getExperimentId, experimentId))
                .stream().collect(Collectors.toMap(RagExperimentJudgementDO::getResumeId,
                        item -> item.getFinalScore() == null ? 0 : item.getFinalScore()));
        List<Long> ranked = resultMapper.selectList(Wrappers.lambdaQuery(RagExperimentResultDO.class)
                        .eq(RagExperimentResultDO::getExperimentId, experimentId).orderByAsc(RagExperimentResultDO::getRankNo))
                .stream().map(RagExperimentResultDO::getResumeId).toList();
        return metricCalculator.calculate(ranked, judgements, topK);
    }

    private Map<Long, List<RagResumeTagDO>> loadTags(Long owner, List<RagExperimentDatasetItemDO> items) {
        Set<Long> resumeIds = items.stream().map(RagExperimentDatasetItemDO::getResumeId).collect(Collectors.toSet());
        return tagMapper.selectList(Wrappers.lambdaQuery(RagResumeTagDO.class)
                        .eq(RagResumeTagDO::getOwnerUserId, owner).in(RagResumeTagDO::getResumeId, resumeIds))
                .stream().collect(Collectors.groupingBy(RagResumeTagDO::getResumeId));
    }

    private int calculateAutomaticScore(List<RagResumeTagDO> tags, List<RagRelevanceRule> rules) {
        for (RagRelevanceRule rule : rules) {
            boolean hit = tags.stream().anyMatch(tag -> tag.getTagType() == rule.tagType()
                    && rule.tagValue().equalsIgnoreCase(tag.getTagValue()));
            if (hit) return rule.score();
        }
        return 0;
    }

    private void validateRules(List<RagRelevanceRule> rules) {
        for (RagRelevanceRule rule : rules) {
            if (rule == null || !isAllowedScore(rule.score())) {
                throw new ClientException("标签判定规则只允许使用 0、1 或 3 分");
            }
        }
    }

    private static Map<String, Object> metricSnapshot(RagExperimentMetrics metrics, long durationMs, int returnedCount,
                                                       int fallbackStageCount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("recallAtK", metrics.recallAtK());
        snapshot.put("precisionAtK", metrics.precisionAtK());
        snapshot.put("mrr", metrics.mrr());
        snapshot.put("ndcgAtK", metrics.ndcgAtK());
        snapshot.put("strongRecallAtK", metrics.strongRecallAtK());
        snapshot.put("returnedCount", returnedCount);
        snapshot.put("relevantCount", metrics.relevantCount());
        snapshot.put("strongRelevantCount", metrics.strongRelevantCount());
        snapshot.put("chunkHitAccuracy", null);
        snapshot.put("chunkHitAccuracyStatus", "未标注");
        snapshot.put("totalDurationMs", durationMs);
        snapshot.put("fallbackStageCount", fallbackStageCount);
        return snapshot;
    }

    /**
     * 仅覆盖证据核验相关字段，保留实验完成时冻结的主检索指标与阶段耗时。
     */
    private void refreshEvidenceMetricSnapshot(RagExperimentDO experiment) {
        List<Boolean> hits = resultMapper.selectList(Wrappers.lambdaQuery(RagExperimentResultDO.class)
                        .eq(RagExperimentResultDO::getExperimentId, experiment.getId()))
                .stream().flatMap(result -> parseEvidence(result.getMatchedChunksJson()).stream())
                .map(this::normalizeEvidence).map(item -> item.getBoolean("hit"))
                .filter(Objects::nonNull).toList();
        Map<String, Object> metrics = new LinkedHashMap<>(jsonMap(experiment.getMetricSnapshotJson()));
        Double accuracy = evidenceMetricCalculator.accuracy(hits);
        metrics.put("chunkHitAccuracy", accuracy);
        metrics.put("chunkHitAccuracyStatus", hits.isEmpty() ? "未标注" : "已标注 " + hits.size() + " 个证据片段");
        experiment.setMetricSnapshotJson(JSON.toJSONString(metrics));
        experimentMapper.updateById(experiment);
    }

    private JSONArray parseEvidence(String raw) {
        if (raw == null || raw.isBlank()) {
            return new JSONArray();
        }
        try {
            JSONArray evidence = JSON.parseArray(raw);
            return evidence == null ? new JSONArray() : evidence;
        } catch (Exception ex) {
            throw new ClientException("实验归档的证据片段格式无效，无法完成标注");
        }
    }

    private JSONObject normalizeEvidence(Object raw) {
        if (raw instanceof JSONObject object) {
            if (!object.containsKey("text")) {
                object.put("text", object.toJSONString());
            }
            return object;
        }
        JSONObject normalized = new JSONObject();
        normalized.put("text", String.valueOf(raw));
        return normalized;
    }

    private static Object parseJson(String value) { return value == null || value.isBlank() ? Map.of() : JSON.parse(value); }
    @SuppressWarnings("unchecked") private static Map<String, Object> jsonMap(String value) {
        return value == null || value.isBlank() ? Map.of() : JSON.parseObject(value, Map.class);
    }
    private static boolean isAllowedScore(int score) { return score == 0 || score == 1 || score == 3; }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static Long parseLong(String value) { try { return Long.valueOf(value); } catch (Exception ignored) { return null; } }
    private static double numeric(Object value) { return value instanceof Number number ? number.doubleValue() : 0D; }
    private static String shortError(Exception ex) { String message = ex.getMessage(); return message == null ? ex.getClass().getSimpleName() : message.substring(0, Math.min(message.length(), 500)); }
}
