package com.hewei.hzyjy.xunzhi.career.resume.rag;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeChunkDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_CHUNK_INDEX;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_CHUNK_TYPE;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_RESUME_ID;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_USER_ID;

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class ResilientResumeVectorStore implements ResumeVectorStore {

    private final ObjectProvider<CareerResumeChunkMapper> chunkMapperProvider;
    private final ObjectProvider<QdrantResumeVectorStore> qdrantVectorStoreProvider;
    private final InMemoryResumeVectorStore inMemoryVectorStore;

    @Override
    public void addAll(List<ResumeVectorDocument> incomingDocuments) {
        if (incomingDocuments == null || incomingDocuments.isEmpty()) {
            return;
        }
        Set<String> resumeIds = incomingDocuments.stream()
                .map(document -> document.metadata().get(META_RESUME_ID))
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toSet());
        inMemoryVectorStore.replaceByResumeIds(resumeIds, incomingDocuments);
        persistChunks(resumeIds, incomingDocuments);
        QdrantResumeVectorStore qdrant = qdrantVectorStoreProvider.getIfAvailable();
        if (qdrant != null && qdrant.available()) {
            try {
                qdrant.addAll(incomingDocuments);
            } catch (Exception ex) {
                log.warn("Qdrant vector write failed, MySQL/in-memory fallback remains available", ex);
            }
        }
    }

    @Override
    public List<ResumeVectorMatch> search(
            float[] queryVector,
            Set<String> chunkTypes,
            Set<String> resumeIds,
            Map<String, String> metadataFilters,
            double minScore,
            int limit) {
        warmupFromDatabase();
        QdrantResumeVectorStore qdrant = qdrantVectorStoreProvider.getIfAvailable();
        if (qdrant != null && qdrant.available()) {
            try {
                List<ResumeVectorMatch> matches = qdrant.search(queryVector, chunkTypes, resumeIds, metadataFilters, minScore, limit);
                if (!matches.isEmpty()) {
                    return matches;
                }
            } catch (Exception ex) {
                log.warn("Qdrant vector search failed, using in-memory fallback", ex);
            }
        }
        return inMemoryVectorStore.search(queryVector, chunkTypes, resumeIds, metadataFilters, minScore, limit);
    }

    @Override
    public List<ResumeVectorDocument> findByResumeIds(Set<String> resumeIds) {
        warmupFromDatabase();
        return inMemoryVectorStore.findByResumeIds(resumeIds);
    }

    private void persistChunks(Set<String> resumeIds, List<ResumeVectorDocument> documents) {
        CareerResumeChunkMapper mapper = chunkMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        Date now = new Date();
        for (String resumeId : resumeIds) {
            parseLong(resumeId).ifPresent(id -> {
                try {
                    mapper.delete(Wrappers.<CareerResumeChunkDO>lambdaQuery().eq(CareerResumeChunkDO::getResumeId, id));
                } catch (Exception ex) {
                    log.debug("Resume chunk cleanup failed. resumeId={}", id, ex);
                }
            });
        }
        for (ResumeVectorDocument document : documents) {
            try {
                Map<String, String> metadata = document.metadata();
                Optional<Long> resumeId = parseLong(metadata.get(META_RESUME_ID));
                if (resumeId.isEmpty()) {
                    continue;
                }
                CareerResumeChunkDO row = new CareerResumeChunkDO();
                row.setResumeId(resumeId.get());
                row.setUserId(parseLong(metadata.get(META_USER_ID)).orElse(null));
                row.setVectorId(document.id());
                row.setChunkType(metadata.get(META_CHUNK_TYPE));
                row.setChunkIndex(parseInt(metadata.get(META_CHUNK_INDEX)));
                row.setContent(document.text());
                row.setVectorJson(JSON.toJSONString(document.vector()));
                row.setMetadataJson(JSON.toJSONString(metadata));
                row.setCreateTime(now);
                row.setUpdateTime(now);
                row.setDelFlag(0);
                mapper.insert(row);
            } catch (Exception ex) {
                log.debug("Resume chunk persistence failed. vectorId={}", document.id(), ex);
            }
        }
    }

    public void warmupFromDatabase() {
        CareerResumeChunkMapper mapper = chunkMapperProvider.getIfAvailable();
        if (mapper == null || !inMemoryVectorStore.isEmpty()) {
            return;
        }
        try {
            List<CareerResumeChunkDO> chunks = mapper.selectList(Wrappers.<CareerResumeChunkDO>lambdaQuery()
                    .eq(CareerResumeChunkDO::getDelFlag, 0));
            List<ResumeVectorDocument> documents = chunks.stream()
                    .map(this::toDocument)
                    .toList();
            inMemoryVectorStore.addAll(documents);
        } catch (Exception ex) {
            log.debug("Resume vector warmup from MySQL failed", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private ResumeVectorDocument toDocument(CareerResumeChunkDO row) {
        Map<String, String> metadata = JSON.parseObject(row.getMetadataJson(), Map.class);
        if (metadata == null) {
            metadata = Map.of(
                    META_RESUME_ID, String.valueOf(row.getResumeId()),
                    META_USER_ID, row.getUserId() == null ? "" : String.valueOf(row.getUserId()),
                    META_CHUNK_TYPE, row.getChunkType(),
                    META_CHUNK_INDEX, String.valueOf(row.getChunkIndex())
            );
        } else if (!metadata.containsKey(META_USER_ID) && row.getUserId() != null) {
            metadata = new java.util.HashMap<>(metadata);
            metadata.put(META_USER_ID, String.valueOf(row.getUserId()));
        }
        float[] vector = JSON.parseObject(row.getVectorJson(), float[].class);
        if (vector == null) {
            vector = new float[0];
        }
        return ResumeVectorDocument.builder()
                .id(row.getVectorId())
                .text(row.getContent())
                .vector(vector)
                .metadata(metadata)
                .build();
    }

    private Optional<Long> parseLong(String value) {
        try {
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private Integer parseInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
