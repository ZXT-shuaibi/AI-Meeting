package com.hewei.hzyjy.xunzhi.career.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 岗位匹配的候选范围配置。
 *
 * <p>候选数量直接影响向量检索、重排与模型调用成本，因此由后端作为唯一权威统一限制；
 * 前端不再保留独立的固定上限，避免配置调整后两端行为不一致。</p>
 */
@Data
@ConfigurationProperties(prefix = "xunzhi-agent.career.job-match")
public class CareerJobMatchProperties {

    /** 默认覆盖中等规模 RAG 对照实验，同时避免单次请求无限扩张。 */
    private int maxCandidateResumes = 200;

    /** 非法配置不放开限制，最小退化为允许一次候选匹配。 */
    public int maxCandidateResumes() {
        return Math.max(1, maxCandidateResumes);
    }

    /** Top-K 不能超过当前部署允许参与检索的最大候选数量。 */
    public int normalizeResultLimit(Integer requestedLimit) {
        int normalized = requestedLimit == null || requestedLimit <= 0 ? 3 : requestedLimit;
        return Math.min(normalized, maxCandidateResumes());
    }

    /** 候选数量校验统一收敛到配置类，避免业务服务重新写死部署上限。 */
    public boolean allowsCandidateCount(int candidateCount) {
        return candidateCount > 0 && candidateCount <= maxCandidateResumes();
    }
}
