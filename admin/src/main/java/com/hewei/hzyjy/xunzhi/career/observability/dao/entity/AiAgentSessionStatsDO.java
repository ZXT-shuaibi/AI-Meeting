package com.hewei.hzyjy.xunzhi.career.observability.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("ai_agent_session_stats")
@EqualsAndHashCode(callSuper = true)
public class AiAgentSessionStatsDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private String sceneCode;
    private Long successCount;
    private Long failureCount;
    private Long totalDurationMs;
    private String lastTraceId;
}
