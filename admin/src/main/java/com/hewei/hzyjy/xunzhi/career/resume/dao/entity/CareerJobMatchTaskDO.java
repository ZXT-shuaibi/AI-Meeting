package com.hewei.hzyjy.xunzhi.career.resume.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("career_job_match_task")
@EqualsAndHashCode(callSuper = true)
public class CareerJobMatchTaskDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private Long userId;
    private String status;
    private String jobDescription;
    private Integer limitCount;
    private String matchedTemplatesJson;
    private String errorMessage;
}
