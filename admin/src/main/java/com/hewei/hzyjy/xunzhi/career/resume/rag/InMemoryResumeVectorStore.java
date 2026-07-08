package com.hewei.hzyjy.xunzhi.career.resume.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_CHUNK_TYPE;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_RESUME_ID;

@Component
public class InMemoryResumeVectorStore {

    private final List<ResumeVectorDocument> documents = new CopyOnWriteArrayList<>();

    public void addAll(List<ResumeVectorDocument> incomingDocuments) {
        if (incomingDocuments == null || incomingDocuments.isEmpty()) {
            return;
        }
        documents.addAll(incomingDocuments);
    }

    public void replaceByResumeIds(Set<String> resumeIds, List<ResumeVectorDocument> incomingDocuments) {
        if (resumeIds != null && !resumeIds.isEmpty()) {
            documents.removeIf(document -> resumeIds.contains(document.metadata().get(META_RESUME_ID)));
        }
        addAll(incomingDocuments);
    }

    public List<ResumeVectorMatch> search(float[] queryVector, Set<String> chunkTypes, Set<String> resumeIds, double minScore, int limit) {
        return documents.stream()
                .filter(document -> matchesFilter(document, chunkTypes, resumeIds))
                .map(document -> ResumeVectorMatch.builder()
                        .document(document)
                        .score(cosine(queryVector, document.vector()))
                        .build())
                .filter(match -> match.score() >= minScore)
                .sorted(Comparator.comparingDouble(ResumeVectorMatch::score).reversed())
                .limit(limit)
                .toList();
    }

    public List<ResumeVectorDocument> findByResumeIds(Set<String> resumeIds) {
        if (resumeIds == null || resumeIds.isEmpty()) {
            return new ArrayList<>(documents);
        }
        return documents.stream()
                .filter(document -> resumeIds.contains(document.metadata().get(META_RESUME_ID)))
                .toList();
    }

    public boolean isEmpty() {
        return documents.isEmpty();
    }

    private boolean matchesFilter(ResumeVectorDocument document, Set<String> chunkTypes, Set<String> resumeIds) {
        if (chunkTypes != null && !chunkTypes.isEmpty() && !chunkTypes.contains(document.metadata().get(META_CHUNK_TYPE))) {
            return false;
        }
        return resumeIds == null || resumeIds.isEmpty() || resumeIds.contains(document.metadata().get(META_RESUME_ID));
    }

    private double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0) {
            return 0.0;
        }
        int len = Math.min(left.length, right.length);
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < len; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
