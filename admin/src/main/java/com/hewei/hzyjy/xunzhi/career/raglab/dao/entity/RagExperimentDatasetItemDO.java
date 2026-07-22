package com.hewei.hzyjy.xunzhi.career.raglab.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RAG 实验数据集中的简历样本持久化对象。
 *
 * <p>该表仅保存对既有简历的引用与稳定展示顺序，不复制简历正文，避免实验数据与简历主数据产生双写。</p>
 */
@Data
@TableName("career_rag_experiment_dataset_item")
@EqualsAndHashCode(callSuper = true)
public class RagExperimentDatasetItemDO extends BaseDO {

    /** 主键，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属 RAG 实验数据集编号。 */
    private Long datasetId;

    /** 拥有该数据集样本关联关系的用户编号。 */
    private Long ownerUserId;

    /** 作为实验样本的既有简历编号。 */
    private Long resumeId;

    /** 当前简历在该数据集内的稳定展示与执行顺序，从小到大排列。 */
    private Integer displayOrder;
}
