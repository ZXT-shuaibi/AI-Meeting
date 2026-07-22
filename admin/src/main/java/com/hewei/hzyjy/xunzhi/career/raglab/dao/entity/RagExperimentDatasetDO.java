package com.hewei.hzyjy.xunzhi.career.raglab.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RAG 实验数据集的持久化对象。
 *
 * <p>数据集只记录实验样本集合的归属与说明；具体简历样本由
 * {@link RagExperimentDatasetItemDO} 按展示顺序关联。</p>
 */
@Data
@TableName("career_rag_experiment_dataset")
@EqualsAndHashCode(callSuper = true)
public class RagExperimentDatasetDO extends BaseDO {

    /** 主键，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建并管理该数据集的用户编号。 */
    private Long ownerUserId;

    /** 面向实验列表展示的数据集名称。 */
    private String name;

    /** 数据集用途、样本范围或构造规则的文字说明。 */
    private String description;
}
