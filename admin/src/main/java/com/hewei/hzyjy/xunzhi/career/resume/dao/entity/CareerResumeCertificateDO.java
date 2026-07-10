package com.hewei.hzyjy.xunzhi.career.resume.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@TableName("career_resume_certificate")
@EqualsAndHashCode(callSuper = true)
public class CareerResumeCertificateDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private Integer itemIndex;
    private String name;
    private String issuer;
    private LocalDate issueDate;
    private String description;
}
