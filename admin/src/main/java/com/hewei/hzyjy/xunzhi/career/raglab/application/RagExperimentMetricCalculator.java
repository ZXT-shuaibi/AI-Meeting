package com.hewei.hzyjy.xunzhi.career.raglab.application;

import com.hewei.hzyjy.xunzhi.career.raglab.model.RagExperimentMetrics;
import org.springframework.stereotype.Component;
import java.util.*;

/** 根据冻结的 0/1/3 真值和实际排名计算检索指标。 */
@Component
public class RagExperimentMetricCalculator {
    public RagExperimentMetrics calculate(List<Long> rankedResumeIds, Map<Long, Integer> judgements, int topK) {
        List<Long> ranked = Optional.ofNullable(rankedResumeIds).orElse(List.of()).stream().limit(Math.max(0, topK)).toList();
        long relevant = judgements.values().stream().filter(value -> value != null && value > 0).count();
        long strong = judgements.values().stream().filter(value -> value != null && value == 3).count();
        long hit = ranked.stream().filter(id -> judgements.getOrDefault(id, 0) > 0).count();
        long strongHit = ranked.stream().filter(id -> judgements.getOrDefault(id, 0) == 3).count();
        double mrr = 0D;
        for (int index = 0; index < ranked.size(); index++) if (judgements.getOrDefault(ranked.get(index), 0) > 0) { mrr = 1D / (index + 1D); break; }
        double dcg = 0D;
        for (int index = 0; index < ranked.size(); index++) dcg += gain(judgements.getOrDefault(ranked.get(index), 0)) / log2(index + 2D);
        List<Integer> ideal = judgements.values().stream().filter(Objects::nonNull).sorted(Comparator.reverseOrder()).limit(ranked.size()).toList();
        double idcg = 0D;
        for (int index = 0; index < ideal.size(); index++) idcg += gain(ideal.get(index)) / log2(index + 2D);
        return new RagExperimentMetrics(relevant == 0 ? 0D : hit / (double) relevant, ranked.isEmpty() ? 0D : hit / (double) ranked.size(), mrr, idcg == 0D ? 0D : dcg / idcg, strong == 0 ? 0D : strongHit / (double) strong, ranked.size(), (int) relevant, (int) strong);
    }
    private double gain(int relevance) { return Math.pow(2D, relevance) - 1D; }
    private double log2(double value) { return Math.log(value) / Math.log(2D); }
}
