package com.hewei.hzyjy.xunzhi.career.harness.dao.entity;
import com.baomidou.mybatisplus.annotation.IdType; import com.baomidou.mybatisplus.annotation.TableId; import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO; import lombok.Data; import lombok.EqualsAndHashCode;
/** 可恢复运行的阶段检查点；只存阶段与脱敏元数据，业务事实仍保留在原任务表、实验表或快照中。 */
@Data @TableName("career_agent_run_checkpoint") @EqualsAndHashCode(callSuper = true)
public class AgentRunCheckpointDO extends BaseDO { @TableId(type=IdType.AUTO) private Long id; private String runId; private Integer sequenceNo; private String checkpointCode; private String metadataJson; }
