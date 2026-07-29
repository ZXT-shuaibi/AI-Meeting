package com.hewei.hzyjy.xunzhi.career.resume.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobMatchTaskResultTest {

    @Test
    void attachesOnlyTheSafeFilteredNoticeToTheTaskResult() {
        JobMatchTaskResult result = JobMatchTaskResult.started("task-1", 7L)
                .withSafetyNotice("已忽略与岗位无关的指令性内容，按提取到的岗位要求完成匹配。");

        assertEquals("已忽略与岗位无关的指令性内容，按提取到的岗位要求完成匹配。", result.safetyNotice());
    }
}
