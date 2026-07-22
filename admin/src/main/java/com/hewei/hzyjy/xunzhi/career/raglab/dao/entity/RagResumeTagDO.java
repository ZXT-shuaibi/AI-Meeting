package com.hewei.hzyjy.xunzhi.career.raglab.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import com.hewei.hzyjy.xunzhi.career.raglab.model.RagResumeTagType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 简历实验标签的持久化对象。
 *
 * <p>标签用于组织和筛选实验简历，当前标签类型限定为岗位（ROLE）或方向（DIRECTION）；
 * 同一简历下相同类型和取值的标签只能保存一次。</p>
 */
@Data
@TableName("career_rag_resume_tag")
@EqualsAndHashCode(callSuper = true)
public class RagResumeTagDO extends BaseDO {

    /** 主键，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建并维护该标签的用户编号。 */
    private Long ownerUserId;

    /** 被标记的既有简历编号。 */
    private Long resumeId;

    /** 标签分类，取值为 ROLE 或 DIRECTION。 */
    private RagResumeTagType tagType;

    /** 标签的实际文本取值，例如目标岗位或职业方向。 */
    private String tagValue;
}
