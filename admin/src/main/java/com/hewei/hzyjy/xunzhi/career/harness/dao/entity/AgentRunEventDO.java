package com.hewei.hzyjy.xunzhi.career.harness.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/** Agent 运行阶段事件：用于详情时间线和后续回放，不存储敏感原文。 */
@Data
@TableName("career_agent_run_event")
@EqualsAndHashCode(callSuper = true)
public class AgentRunEventDO extends BaseDO {
    private Long id;
    private String runId;
    private String eventType;
    private String stageCode;
    private String status;
    private String message;
    private String metadataJson;
    private Date occurredAt;
}
