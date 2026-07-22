package com.hewei.hzyjy.xunzhi.career.raglab.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 一次 RAG 实验运行的持久化对象。
 *
 * <p>除实验输入和状态外，本表冻结运行时配置及评判规则快照，使同一实验可被稳定复盘和横向比较。</p>
 */
@Data
@TableName("career_rag_experiment")
@EqualsAndHashCode(callSuper = true)
public class RagExperimentDO extends BaseDO {

    /** 主键，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起并拥有本次实验的用户编号。 */
    private Long ownerUserId;

    /** 供实验中心列表展示和人工识别的实验名称。 */
    private String name;

    /** 本次实验使用的数据集编号。 */
    private Long datasetId;

    /** 作为 RAG 查询条件的岗位描述原文。 */
    private String jobDescription;

    /** 每次检索保留的候选数量。 */
    private Integer topK;

    /** 实验生命周期状态，例如 PENDING、RUNNING、COMPLETED 或 FAILED。 */
    private String status;

    /** 运行时实际生效的检索、重排和模型配置 JSON 快照。 */
    private String runtimeConfigJson;

    /** 本次实验实际使用的评判规则 JSON 快照。 */
    private String judgementSnapshotJson;

    /** 由关键配置生成的稳定指纹，用于快速识别同配置实验。 */
    private String configFingerprint;

    /** 实验开始执行的业务时间。 */
    private Date startedAt;

    /** 实验结束执行的业务时间，无论成功或失败均可记录。 */
    private Date completedAt;

    /** 实验失败、降级或中断时供列表展示的摘要。 */
    private String errorSummary;

    /** 用于同组参数对照的比较分组标识。 */
    private String comparisonGroup;
}
