package com.hewei.hzyjy.xunzhi.career.resume.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("career_resume_format_meta")
@EqualsAndHashCode(callSuper = true)
public class CareerResumeFormatMetaDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private String alignment;
    private Double lineSpacing;
    private String fontFamily;
    private String datePattern;
    private String hyperlinkStyle;
    private Boolean showAvatar;
    private Boolean showSocial;
    private Boolean twoColumnLayout;
}
