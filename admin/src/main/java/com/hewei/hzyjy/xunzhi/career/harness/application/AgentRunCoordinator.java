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
        finish(runId, AgentRunStatus.SUCCEEDED, resultSummary, null, metadata);
    }

    public void fail(String runId, String errorMessage, Map<String, Object> metadata) {
        finish(runId, AgentRunStatus.FAILED, null, errorMessage, metadata);
    }

    private void finish(
            String runId, AgentRunStatus status, String resultSummary, String errorMessage, Map<String, Object> metadata) {
        Date now = new Date();
        AgentRunDO update = new AgentRunDO();
        update.setStatus(status.name());
        update.setResultSummary(resultSummary);
        update.setErrorMessage(errorMessage);
        update.setFinishedAt(now);
        update.setMetadataJson(JSON.toJSONString(metadata == null ? Map.of() : metadata));
        agentRunMapper.update(update, Wrappers.<AgentRunDO>lambdaUpdate().eq(AgentRunDO::getRunId, runId));
        appendEvent(
                runId,
                status == AgentRunStatus.SUCCEEDED ? "RUN_FINISHED" : "RUN_FAILED",
                "RUN",
                status.name(),
                status == AgentRunStatus.SUCCEEDED ? resultSummary : errorMessage,
                metadata
        );
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
}
