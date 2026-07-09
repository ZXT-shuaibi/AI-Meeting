package com.hewei.hzyjy.xunzhi.career.resume.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@TableName("career_resume_parse_task")
@EqualsAndHashCode(callSuper = true)
public class CareerResumeParseTaskDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private Long userId;
    private String status;
    private Long resumeId;
    private String originalFilename;
    private Long fileSize;
    private String contentType;
    private String cvType;
    private String storageProvider;
    private String storageKey;
    private String filePath;
    private byte[] fileSnapshot;
    private String retryOfTaskId;
    private String errorMessage;
    private Date startTime;
    private Date completeTime;
}
