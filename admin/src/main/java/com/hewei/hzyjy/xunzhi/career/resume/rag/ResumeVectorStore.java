package com.hewei.hzyjy.xunzhi.career.resume.rag;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ResumeVectorStore {

    void addAll(List<ResumeVectorDocument> documents);

    default List<ResumeVectorMatch> search(float[] queryVector, Set<String> chunkTypes, Set<String> resumeIds, double minScore, int limit) {
        return search(queryVector, chunkTypes, resumeIds, Map.of(), minScore, limit);
    }

    List<ResumeVectorMatch> search(
            float[] queryVector,
            Set<String> chunkTypes,
            Set<String> resumeIds,
            Map<String, String> metadataFilters,
            double minScore,
            int limit);

    List<ResumeVectorDocument> findByResumeIds(Set<String> resumeIds);
}
