package com.hewei.hzyjy.xunzhi.career.raglab.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * RAG 实验室访问权限的持久化对象。
 *
 * <p>一名用户在权限表中至多拥有一条记录，用于在不改动既有简历 RAG 主链路的前提下，
 * 控制其是否可以使用实验、对比和评判能力。</p>
 */
@Data
@TableName("career_rag_lab_permission")
@EqualsAndHashCode(callSuper = true)
public class RagLabPermissionDO extends BaseDO {

    /** 主键，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被授予或被禁用 RAG 实验室能力的用户编号。 */
    private Long userId;

    /** 权限开关：true 表示允许进入实验室，false 表示明确禁用。 */
    private Boolean enabled;

    /** 执行授权或禁用动作的管理用户编号。 */
    private Long grantedByUserId;

    /** 最近一次授予或更新该权限的业务时间。 */
    private Date grantedAt;
}
