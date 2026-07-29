package com.hewei.hzyjy.xunzhi.career.harness.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentRunMapper;
import com.hewei.hzyjy.xunzhi.career.harness.model.AgentToolCode;
import com.hewei.hzyjy.xunzhi.career.harness.model.AgentToolCommand;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagExperimentDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagExperimentMapper;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Agent 工具调用安全边界。
 * <p>本类不是通用执行器：工具代码必须在白名单中；每个工具仅接收资源 ID；资源 owner 必须与调用用户一致。
 * 因此模型无法通过本入口执行命令、读取任意文件、访问外部 URL 或绕开业务数据隔离。</p>
 */
@Service
public class AgentToolGateway {
    private final RagExperimentMapper experimentMapper;
    private final AgentRunMapper runMapper;
    private final AgentRunCoordinator coordinator;

    public AgentToolGateway(RagExperimentMapper experimentMapper, AgentRunMapper runMapper, AgentRunCoordinator coordinator) {
        this.experimentMapper = experimentMapper; this.runMapper = runMapper; this.coordinator = coordinator;
    }

    public Map<String, Object> execute(AgentToolCommand command) {
        if (command == null || command.toolCode() == null || command.callerUserId() == null || command.resourceId() == null || command.resourceId().isBlank()) {
            throw new ClientException("工具调用参数不完整或工具不在白名单中");
        }
        verifyParentRunOwnership(command.callerUserId(), command.parentRunId());
        try {
            Map<String, Object> result = switch (command.toolCode()) {
                case READ_RAG_EXPERIMENT_SUMMARY -> readRagExperiment(command.callerUserId(), parseLong(command.resourceId()));
                case READ_AGENT_RUN_SUMMARY -> readAgentRun(command.callerUserId(), command.resourceId().trim());
            };
            audit(command, true, result.size());
            return result;
        } catch (ClientException ex) {
            // 父运行已验证为调用者本人时，拒绝事件可安全回写到同一条时间线；绝不携带资源内容或异常详情。
            audit(command, false, 0);
            throw ex;
        }
    }

    /** 父运行仅用于关联当前用户已授权的时间线，不能成为向他人运行写入事件的通道。 */
    private void verifyParentRunOwnership(Long callerUserId, String parentRunId) {
        if (parentRunId == null || parentRunId.isBlank()) return;
        AgentRunDO parentRun = runMapper.selectList(Wrappers.<AgentRunDO>lambdaQuery()
                        .eq(AgentRunDO::getRunId, parentRunId.trim())
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        if (parentRun == null || !Objects.equals(callerUserId, parentRun.getUserId())) {
            throw new ClientException("无权关联指定的 Agent 运行记录");
        }
    }

    private Map<String, Object> readRagExperiment(Long caller, Long experimentId) {
        RagExperimentDO item = experimentId == null ? null : experimentMapper.selectById(experimentId);
        if (item == null || !Objects.equals(caller, item.getOwnerUserId())) throw new ClientException("无权读取该 RAG 实验摘要");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("experimentId", String.valueOf(item.getId())); result.put("name", item.getName()); result.put("status", item.getStatus());
        result.put("topK", item.getTopK()); result.put("configFingerprint", item.getConfigFingerprint()); result.put("metricSnapshot", item.getMetricSnapshotJson());
        return result;
    }

    private Map<String, Object> readAgentRun(Long caller, String runId) {
        AgentRunDO item = runMapper.selectList(Wrappers.<AgentRunDO>lambdaQuery().eq(AgentRunDO::getRunId, runId).last("LIMIT 1")).stream().findFirst().orElse(null);
        if (item == null || !Objects.equals(caller, item.getUserId())) throw new ClientException("无权读取该 Agent 运行摘要");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", item.getRunId()); result.put("sceneCode", item.getSceneCode()); result.put("status", item.getStatus());
        result.put("ragEnabled", Boolean.TRUE.equals(item.getRagEnabled())); result.put("durationMs", item.getDurationMs()); result.put("traceId", item.getTraceId());
        return result;
    }

    private void audit(AgentToolCommand command, boolean success, int outputFieldCount) {
        if (coordinator == null || command.parentRunId() == null || command.parentRunId().isBlank()) return;
        String message = success ? "已完成受控只读工具调用" : "受控只读工具调用已被权限校验拒绝";
        try { coordinator.stage(command.parentRunId(), "TOOL_" + command.toolCode().name(), message, Map.of("resultCount", outputFieldCount, "success", success)); }
        catch (Exception ignored) { /* 工具审计不可影响主工具读取结果。 */ }
    }
    private Long parseLong(String value) { try { return Long.valueOf(value.trim()); } catch (Exception ignored) { return null; } }
}
