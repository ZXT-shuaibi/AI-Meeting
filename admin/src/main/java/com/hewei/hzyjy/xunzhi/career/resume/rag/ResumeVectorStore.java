package com.hewei.hzyjy.xunzhi.career.resume.rag;

import java.util.List;
import java.util.Set;

public interface ResumeVectorStore {

    void addAll(List<ResumeVectorDocument> documents);

    List<ResumeVectorMatch> search(float[] queryVector, Set<String> chunkTypes, Set<String> resumeIds, double minScore, int limit);

    List<ResumeVectorDocument> findByResumeIds(Set<String> resumeIds);
}
