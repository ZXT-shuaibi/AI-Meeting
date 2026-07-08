package com.hewei.hzyjy.xunzhi.career.observability.dao.entity;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.career.observability.AiToolExecutionEvent;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@TableName("ai_tool_execution")
@EqualsAndHashCode(callSuper = true)
public class AiToolExecutionDO extends BaseDO {

    private Long id;
    private String traceId;
    private String sessionId;
    private String agentId;
    private String sceneCode;
    private String toolName;
    private String toolInput;
    private String toolOutput;
    private Boolean success;
    private Long executionTimeMs;
    private Integer invocationOrder;
    private String errorMessage;
    private Date eventTime;
    private String metadataJson;

    public static AiToolExecutionDO from(AiToolExecutionEvent event) {
        AiToolExecutionDO entity = new AiToolExecutionDO();
        entity.setTraceId(event.traceId());
        entity.setSessionId(event.sessionId());
        entity.setAgentId(event.agentId());
        entity.setSceneCode(event.sceneCode());
        entity.setToolName(event.toolName());
        entity.setToolInput(event.toolInput());
        entity.setToolOutput(event.toolOutput());
        entity.setSuccess(event.success());
        entity.setExecutionTimeMs(event.executionTimeMs());
        entity.setInvocationOrder(event.invocationOrder());
        entity.setErrorMessage(event.errorMessage());
        entity.setEventTime(Date.from(event.eventTime()));
        entity.setMetadataJson(JSON.toJSONString(event.metadata()));
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        entity.setDelFlag(0);
        return entity;
    }
}
