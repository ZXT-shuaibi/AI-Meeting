package com.hewei.hzyjy.xunzhi.career.observability.dao.entity;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.career.observability.AiInvocationCompletedEvent;
import com.hewei.hzyjy.xunzhi.career.observability.AiInvocationFailedEvent;
import com.hewei.hzyjy.xunzhi.career.observability.AiInvocationStartedEvent;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@TableName("ai_invocation_trace")
@EqualsAndHashCode(callSuper = true)
public class AiInvocationTraceDO extends BaseDO {

    private Long id;
    private String traceId;
    private String sessionId;
    private Long userId;
    private String agentId;
    private String agentName;
    private String sceneCode;
    private String stage;
    private String provider;
    private String modelOrFlowId;
    private String eventType;
    private Date startTime;
    private Date endTime;
    private Long durationMs;
    private String inputSummary;
    private String outputSummary;
    private String errorMessage;
    private String errorStackTrace;
    private String requestKey;
    private Boolean stream;
    private String metadataJson;

    public static AiInvocationTraceDO fromStarted(AiInvocationStartedEvent event) {
        AiInvocationTraceDO trace = base(event.traceId(), event.sessionId(), event.userId(), event.agentId(),
                event.agentName(), event.sceneCode(), event.stage(), event.provider(), event.modelOrFlowId());
        trace.setEventType("START");
        trace.setStartTime(Date.from(event.startTime()));
        trace.setInputSummary(event.inputSummary());
        trace.setRequestKey(event.requestKey());
        trace.setStream(event.stream());
        trace.setMetadataJson(JSON.toJSONString(event.metadata()));
        return trace;
    }

    public static AiInvocationTraceDO fromCompleted(AiInvocationCompletedEvent event) {
        AiInvocationTraceDO trace = base(event.traceId(), event.sessionId(), event.userId(), event.agentId(),
                event.agentName(), event.sceneCode(), event.stage(), event.provider(), event.modelOrFlowId());
        trace.setEventType("END");
        trace.setStartTime(Date.from(event.startTime()));
        trace.setEndTime(Date.from(event.endTime()));
        trace.setDurationMs(event.durationMs());
        trace.setInputSummary(event.inputSummary());
        trace.setOutputSummary(event.outputSummary());
        trace.setRequestKey(event.requestKey());
        trace.setStream(event.stream());
        trace.setMetadataJson(JSON.toJSONString(event.metadata()));
        return trace;
    }

    public static AiInvocationTraceDO fromFailed(AiInvocationFailedEvent event) {
        AiInvocationTraceDO trace = base(event.traceId(), event.sessionId(), event.userId(), event.agentId(),
                event.agentName(), event.sceneCode(), event.stage(), event.provider(), event.modelOrFlowId());
        trace.setEventType("ERROR");
        trace.setStartTime(Date.from(event.startTime()));
        trace.setEndTime(Date.from(event.endTime()));
        trace.setDurationMs(event.durationMs());
        trace.setInputSummary(event.inputSummary());
        trace.setErrorMessage(event.errorMessage());
        trace.setErrorStackTrace(event.errorStackTrace());
        trace.setRequestKey(event.requestKey());
        trace.setStream(event.stream());
        trace.setMetadataJson(JSON.toJSONString(event.metadata()));
        return trace;
    }

    private static AiInvocationTraceDO base(
            String traceId,
            String sessionId,
            Long userId,
            String agentId,
            String agentName,
            String sceneCode,
            String stage,
            String provider,
            String modelOrFlowId) {
        AiInvocationTraceDO trace = new AiInvocationTraceDO();
        trace.setTraceId(traceId);
        trace.setSessionId(sessionId);
        trace.setUserId(userId);
        trace.setAgentId(agentId);
        trace.setAgentName(agentName);
        trace.setSceneCode(sceneCode);
        trace.setStage(stage);
        trace.setProvider(provider);
        trace.setModelOrFlowId(modelOrFlowId);
        return trace;
    }
}
