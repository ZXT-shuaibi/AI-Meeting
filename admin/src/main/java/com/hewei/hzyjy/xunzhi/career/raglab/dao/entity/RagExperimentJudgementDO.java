package com.hewei.hzyjy.xunzhi.career.raglab.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 单份简历在 RAG 实验中的评判持久化对象。
 *
 * <p>自动评分与最终评分分离保存，以支持在保留规则快照的同时记录人工覆写结论。</p>
 */
@Data
@TableName("career_rag_experiment_judgement")
@EqualsAndHashCode(callSuper = true)
public class RagExperimentJudgementDO extends BaseDO {

    /** 主键，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属 RAG 实验编号。 */
    private Long experimentId;

    /** 拥有该实验及评分记录的用户编号。 */
    private Long ownerUserId;

    /** 被评判的既有简历编号。 */
    private Long resumeId;

    /** 按规则自动计算得到的原始评分。 */
    private BigDecimal automaticScore;

    /** 最终采用的评分；人工覆写后可与自动评分不同。 */
    private BigDecimal finalScore;

    /** 生成该评分时实际生效的规则 JSON 快照。 */
    private String ruleSnapshotJson;

    /** 人工调整最终评分时必须记录的原因说明。 */
    private String overrideReason;
}
