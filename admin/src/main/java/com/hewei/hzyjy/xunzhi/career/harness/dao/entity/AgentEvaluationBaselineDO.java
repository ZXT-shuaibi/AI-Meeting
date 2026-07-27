package com.hewei.hzyjy.xunzhi.career.harness.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 固化一次已完成实验的配置与指标，用于后续可复现回归对比。 */
@Data
@TableName("career_agent_evaluation_baseline")
@EqualsAndHashCode(callSuper = true)
public class AgentEvaluationBaselineDO extends BaseDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private String name;
    private String sceneCode;
    private Long sourceExperimentId;
    private String configFingerprint;
    private String runtimeConfigJson;
    private String metricSnapshotJson;
}
