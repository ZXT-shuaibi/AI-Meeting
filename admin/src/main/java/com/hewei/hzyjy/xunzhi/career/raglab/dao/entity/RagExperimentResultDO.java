package com.hewei.hzyjy.xunzhi.career.raglab.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hewei.hzyjy.xunzhi.common.database.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * RAG 实验中单份简历的检索与评分结果持久化对象。
 *
 * <p>结果表保存排名、相关性、RAG 分数和执行快照，供实验对比页面复原结果与定位降级链路。</p>
 */
@Data
@TableName("career_rag_experiment_result")
@EqualsAndHashCode(callSuper = true)
public class RagExperimentResultDO extends BaseDO {

    /** 主键，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属 RAG 实验编号。 */
    private Long experimentId;

    /** 拥有该实验结果的用户编号。 */
    private Long ownerUserId;

    /** 参与本次检索和排序的既有简历编号。 */
    private Long resumeId;

    /** 最终排序名次，数值越小表示排名越靠前。 */
    private Integer rankNo;

    /** 简历与岗位描述之间的相关性评分。 */
    private BigDecimal relevanceScore;

    /** RAG 检索、重排及生成链路汇总后的结果评分。 */
    private BigDecimal ragScore;

    /** 与该结果匹配的分块标识及必要摘要 JSON。 */
    private String matchedChunksJson;

    /** 各执行阶段耗时、命中和降级信息的 JSON 追踪快照。 */
    private String stageTraceJson;

    /** 从请求进入到结果产出的总耗时，单位为毫秒。 */
    private Long totalDurationMs;

    /** 检索或重排链路中发生的降级阶段数量。 */
    private Integer fallbackStageCount;

    /** 供结果页稳定展示的完整结果 JSON 快照。 */
    private String resultSnapshotJson;
}
