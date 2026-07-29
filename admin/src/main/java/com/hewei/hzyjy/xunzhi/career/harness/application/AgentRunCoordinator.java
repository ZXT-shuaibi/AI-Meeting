package com.hewei.hzyjy.xunzhi.career.harness.application;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunEventDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentRunEventMapper;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentRunMapper;
import com.hewei.hzyjy.xunzhi.career.harness.model.AgentRunStartCommand;
import com.hewei.hzyjy.xunzhi.career.harness.model.AgentRunStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * 第一层 Harness 运行协调器。
 *
 * <p>它只负责写入一次业务运行及其阶段时间线，不接管现有面试快照、RAG 实验状态或异步任务的事实来源。
 * 业务模块在自己的状态机完成后调用此处记录过程，因此不会形成第二套业务状态。</p>
 */
@Service
@RequiredArgsConstructor
public class AgentRunCoordinator {

    private final AgentRunMapper agentRunMapper;
    private final AgentRunEventMapper agentRunEventMapper;

    /** Outbox 未建表或外部桥未配置时不得影响主任务完成。 */
    @Autowired(required = false)
    private AgentNotificationOutboxService notificationOutboxService;

    public String start(AgentRunStartCommand command) {
        String runId = "agent-run-" + UUID.randomUUID();
        Date now = new Date();
        AgentRunDO run = new AgentRunDO();
        run.setRunId(runId);
        run.setTraceId(command.traceId());
        run.setUserId(command.userId());
        run.setBusinessType(command.businessType());
        run.setBusinessId(command.businessId());
        run.setSceneCode(command.sceneCode());
        run.setSessionId(command.sessionId());
        run.setStatus(AgentRunStatus.RUNNING.name());
        run.setRagEnabled(command.ragEnabled());
        run.setInputSummary(command.inputSummary());
        run.setConfigFingerprint(command.configFingerprint());
        run.setStartedAt(now);
        run.setMetadataJson(JSON.toJSONString(Map.of("schemaVersion", 1)));
        agentRunMapper.insert(run);
        appendEvent(runId, "RUN_CREATED", "RUN", AgentRunStatus.RUNNING.name(), "运行已创建", Map.of());
        return runId;
    }

    public void stage(String runId, String stageCode, String message, Map<String, Object> metadata) {
        appendEvent(runId, stageCode, stageCode, AgentRunStatus.RUNNING.name(), message, metadata);
    }

    public void succeed(String runId, String resultSummary, Map<String, Object> metadata) {
        if (finish(runId, AgentRunStatus.SUCCEEDED, resultSummary, null, metadata)) {
            enqueueCompletionNotification(runId);
        }
    }

    public void fail(String runId, String errorMessage, Map<String, Object> metadata) {
        finish(runId, AgentRunStatus.FAILED, null, errorMessage, metadata);
    }

    private boolean finish(
            String runId, AgentRunStatus status, String resultSummary, String errorMessage, Map<String, Object> metadata) {
        Date now = new Date();
        AgentRunDO existing = agentRunMapper.selectList(Wrappers.<AgentRunDO>lambdaQuery()
                        .eq(AgentRunDO::getRunId, runId)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        // 取消是协作式请求：部分旧业务链路尚未在每个外部调用后检查取消标记。
        // 这类任务返回时不能再把 CANCEL_REQUESTED 覆盖为成功或失败，否则运行会永久卡在“取消请求中”。
        boolean cancellationPending = existing != null
                && AgentRunStatus.CANCEL_REQUESTED.name().equals(existing.getStatus());
        AgentRunStatus terminalStatus = cancellationPending ? AgentRunStatus.CANCELLED : status;
        AgentRunDO update = new AgentRunDO();
        update.setStatus(terminalStatus.name());
        update.setResultSummary(cancellationPending ? null : resultSummary);
        update.setErrorMessage(cancellationPending
                ? (existing.getErrorMessage() == null ? "管理员请求取消，已在任务返回后确认取消" : existing.getErrorMessage())
                : errorMessage);
        update.setFinishedAt(now);
        update.setDurationMs(existing == null || existing.getStartedAt() == null
                ? 0L
                : Math.max(0L, now.getTime() - existing.getStartedAt().getTime()));
        update.setMetadataJson(JSON.toJSONString(metadata == null ? Map.of() : metadata));
        int affected = agentRunMapper.update(update, Wrappers.<AgentRunDO>lambdaUpdate()
                .eq(AgentRunDO::getRunId, runId)
                .eq(AgentRunDO::getStatus, cancellationPending
                        ? AgentRunStatus.CANCEL_REQUESTED.name()
                        : AgentRunStatus.RUNNING.name()));
        if (affected <= 0) {
            return false;
        }
        appendEvent(
                runId,
                terminalStatus == AgentRunStatus.CANCELLED ? "RUN_CANCELLED"
                        : status == AgentRunStatus.SUCCEEDED ? "RUN_FINISHED" : "RUN_FAILED",
                "RUN",
                terminalStatus.name(),
                terminalStatus == AgentRunStatus.CANCELLED ? update.getErrorMessage()
                        : status == AgentRunStatus.SUCCEEDED ? resultSummary : errorMessage,
                metadata
        );
        return true;
    }

    private void appendEvent(
            String runId, String eventType, String stageCode, String status, String message, Map<String, Object> metadata) {
        AgentRunEventDO event = new AgentRunEventDO();
        event.setRunId(runId);
        event.setEventType(eventType);
        event.setStageCode(stageCode);
        event.setStatus(status);
        event.setMessage(message);
        event.setMetadataJson(JSON.toJSONString(metadata == null ? Map.of() : metadata));
        event.setOccurredAt(new Date());
        agentRunEventMapper.insert(event);
    }

    private void enqueueCompletionNotification(String runId) {
        if (notificationOutboxService == null || runId == null) return;
        try {
            AgentRunDO run = agentRunMapper.selectList(Wrappers.<AgentRunDO>lambdaQuery()
                    .eq(AgentRunDO::getRunId, runId).last("LIMIT 1")).stream().findFirst().orElse(null);
            notificationOutboxService.enqueueCompletion(run);
        } catch (Exception ex) {
            // 外部通知是可选副作用；运行总账和业务结果已经成功时绝不反向失败。
            org.slf4j.LoggerFactory.getLogger(AgentRunCoordinator.class)
                    .warn("Agent 完成通知入队失败，保留主运行成功状态。runId={}", runId, ex);
        }
    }
}
