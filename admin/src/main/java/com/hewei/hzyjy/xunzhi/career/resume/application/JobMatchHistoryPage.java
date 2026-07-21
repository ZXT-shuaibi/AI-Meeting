package com.hewei.hzyjy.xunzhi.career.resume.application;

import java.util.List;

/**
 * 岗位匹配历史列表的分页结果。
 *
 * <p>{@code items} 是本次请求返回的历史任务，{@code total} 是当前用户全部历史任务数量；
 * 前端可据此在“最近 N 条”和“全部”两种查看模式间切换，而无需重复统计。</p>
 */
public record JobMatchHistoryPage(List<JobMatchHistoryItem> items, long total) {
    public JobMatchHistoryPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
