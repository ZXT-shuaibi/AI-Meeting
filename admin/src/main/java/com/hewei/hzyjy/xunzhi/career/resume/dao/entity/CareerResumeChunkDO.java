package com.hewei.hzyjy.xunzhi.career.resume.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("career_resume_chunk")
@EqualsAndHashCode(callSuper = true)
public class CareerResumeChunkDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private String vectorId;
    private String chunkType;
    private Integer chunkIndex;
    private String content;
    private String vectorJson;
    private String metadataJson;
}
