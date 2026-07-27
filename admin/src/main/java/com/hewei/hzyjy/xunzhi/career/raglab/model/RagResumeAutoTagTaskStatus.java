package com.hewei.hzyjy.xunzhi.career.raglab.model;

/** AI 简历预评异步任务的生命周期状态。 */
public enum RagResumeAutoTagTaskStatus {
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED;
    }
}
