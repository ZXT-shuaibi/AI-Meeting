package com.hewei.hzyjy.xunzhi.career.harness.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Date;

/** 通知 Outbox：业务完成与外部发送解耦，失败后仍保留可审计、可重试的通知事实。 */
@Data @TableName("career_agent_notification_outbox") @EqualsAndHashCode(callSuper = true)
public class AgentNotificationOutboxDO extends BaseDO {
    @TableId(type = IdType.AUTO) private Long id;
    private String deliveryKey; private String runId; private Long userId; private String channel;
    private String status; private String title; private String payloadJson; private Integer attemptCount;
    private String errorSummary; private Date deliveredAt;
}
