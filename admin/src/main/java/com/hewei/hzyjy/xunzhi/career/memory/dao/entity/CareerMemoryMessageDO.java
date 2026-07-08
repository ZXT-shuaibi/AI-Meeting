package com.hewei.hzyjy.xunzhi.career.memory.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@Data
@TableName("career_memory_message")
@EqualsAndHashCode(callSuper = true)
public class CareerMemoryMessageDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String memoryId;
    private Integer messageIndex;
    private String role;
    private String content;
    private Instant messageTime;
    private String metadataJson;
}