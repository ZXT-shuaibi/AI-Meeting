package com.hewei.hzyjy.xunzhi.career.resume.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantResumeVectorStore {

    public void addAll(List<ResumeVectorDocument> incomingDocuments) {
        // The concrete Qdrant client is intentionally isolated here. When LangChain4j/Qdrant
        // dependencies are enabled, this adapter can be filled without changing business code.
        throw new UnsupportedOperationException("Qdrant adapter is not active in this runtime");
    }

    public List<ResumeVectorMatch> search(float[] queryVector, Set<String> chunkTypes, Set<String> resumeIds, double minScore, int limit) {
        throw new UnsupportedOperationException("Qdrant adapter is not active in this runtime");
    }

    public boolean available() {
        return false;
    }
}
