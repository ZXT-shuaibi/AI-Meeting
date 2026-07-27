package com.hewei.hzyjy.xunzhi.career.harness.model;

/**
 * Agent 业务运行的统一生命周期。
 *
 * <p>降级不是独立状态：任务即使走了本地兜底，只要最终产出可用结果仍记为
 * {@link #SUCCEEDED}，并通过事件元数据标记降级原因，避免把“可用但降级”和“任务失败”混为一谈。</p>
 */
public enum AgentRunStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT
}
