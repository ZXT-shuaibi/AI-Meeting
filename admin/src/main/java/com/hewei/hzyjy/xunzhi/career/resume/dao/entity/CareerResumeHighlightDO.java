package com.hewei.hzyjy.xunzhi.career.resume.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("career_resume_highlight")
@EqualsAndHashCode(callSuper = true)
public class CareerResumeHighlightDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private String ownerType;
    private Integer ownerIndex;
    private Integer itemIndex;
    private String type;
    private String relatedId;
    private String highlight;
}
