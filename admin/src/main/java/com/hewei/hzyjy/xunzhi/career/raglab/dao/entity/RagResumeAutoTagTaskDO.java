package com.hewei.hzyjy.xunzhi.career.raglab.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * AI 简历预评任务持久化对象。
 *
 * <p>activeKey 在任务运行期间唯一，任务结束后置空，从数据库层阻止同一用户对同一简历重复发起模型调用。</p>
 */
@Data
@TableName("career_rag_resume_auto_tag_task")
@EqualsAndHashCode(callSuper = true)
public class RagResumeAutoTagTaskDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private Long ownerUserId;
    private Long resumeId;
    private String activeKey;
    private String status;
    private Integer tagCount;
    private String errorMessage;
    private Date startedAt;
    private Date completedAt;
}
