package com.hewei.hzyjy.xunzhi.career.resume.application;

import java.time.Instant;
import java.util.List;

/**
 * 当前用户的岗位匹配历史摘要。
 *
 * <p>仅承载历史列表展示所需的任务、候选简历、召回结果与耗时信息，避免历史列表查询再次加载
 * 大体积简历正文或完整 RAG 分片。</p>
 */
public record JobMatchHistoryItem(
        String taskId,
        String status,
        String jobDescription,
        int selectedResumeCount,
        List<Long> selectedResumeIds,
        int requestedTopK,
        List<JobMatchCandidate> matchedResumes,
        String errorMessage,
        Instant createdAt
) {
    public JobMatchHistoryItem {
        selectedResumeIds = selectedResumeIds == null ? List.of() : List.copyOf(selectedResumeIds);
        matchedResumes = matchedResumes == null ? List.of() : List.copyOf(matchedResumes);
    }
}
