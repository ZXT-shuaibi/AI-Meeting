package com.hewei.hzyjy.xunzhi.career.harness.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/** Agent 运行总账：一条记录对应一次可审计的业务任务。 */
@Data
@TableName("career_agent_run")
@EqualsAndHashCode(callSuper = true)
public class AgentRunDO extends BaseDO {
    private Long id;
    private String runId;
    private String traceId;
    private Long userId;
    private String businessType;
    private String businessId;
    private String sceneCode;
    private String sessionId;
    private String status;
    private Boolean ragEnabled;
    private String inputSummary;
    private String configFingerprint;
    private String resultSummary;
    private String errorMessage;
    private String metadataJson;
    private Date startedAt;
    private Date finishedAt;
    private Long durationMs;
}
