package com.hewei.hzyjy.xunzhi.career.raglab.model;

/** 返回给前端的预评任务摘要；标签内容由任务完成后原有标签接口统一读取。 */
public record RagResumeAutoTagTaskResult(
        String taskId,
        RagResumeAutoTagTaskStatus status,
        Integer tagCount,
        String errorMessage,
        boolean reused
) {
}
