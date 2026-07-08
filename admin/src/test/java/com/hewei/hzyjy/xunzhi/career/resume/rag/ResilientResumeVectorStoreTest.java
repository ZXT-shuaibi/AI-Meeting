package com.hewei.hzyjy.xunzhi.career.resume.rag;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeChunkDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeChunkMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.CHUNK_TYPE_PROJECT;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_CHUNK_INDEX;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_CHUNK_TYPE;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_RESUME_ID;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResilientResumeVectorStoreTest {

    @Test
    void persistsUserIdAsFirstClassChunkColumn() {
        CareerResumeChunkMapper mapper = mock(CareerResumeChunkMapper.class);
        when(mapper.insert(any(CareerResumeChunkDO.class))).thenReturn(1);
        ResilientResumeVectorStore store = new ResilientResumeVectorStore(
                provider(mapper),
                emptyProvider(),
                new InMemoryResumeVectorStore()
        );
        ResumeVectorDocument document = ResumeVectorDocument.builder()
                .id("vector-201")
                .text("Spring AI LangChain4j Redis project")
                .vector(new float[]{0.1F, 0.2F})
                .metadata(Map.of(
                        META_RESUME_ID, "201",
                        META_USER_ID, "7",
                        META_CHUNK_TYPE, CHUNK_TYPE_PROJECT,
                        META_CHUNK_INDEX, "0"
                ))
                .build();

        store.addAll(List.of(document));

        ArgumentCaptor<CareerResumeChunkDO> rowCaptor = ArgumentCaptor.forClass(CareerResumeChunkDO.class);
        verify(mapper).insert(rowCaptor.capture());
        assertEquals(7L, rowCaptor.getValue().getUserId());
    }

    @Test
    void restoresUserIdMetadataFromFirstClassChunkColumnWhenMetadataJsonIsMissingIt() {
        CareerResumeChunkDO row = new CareerResumeChunkDO();
        row.setResumeId(201L);
        row.setUserId(7L);
        row.setVectorId("vector-201");
        row.setChunkType(CHUNK_TYPE_PROJECT);
        row.setChunkIndex(0);
        row.setContent("Spring AI LangChain4j Redis project");
        row.setVectorJson("[0.1,0.2]");
        row.setMetadataJson("{\"resume_id\":\"201\",\"chunk_type\":\"project\",\"chunk_index\":\"0\"}");
        row.setDelFlag(0);
        CareerResumeChunkMapper mapper = mock(CareerResumeChunkMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
        ResilientResumeVectorStore store = new ResilientResumeVectorStore(
                provider(mapper),
                emptyProvider(),
                new InMemoryResumeVectorStore()
        );

        List<ResumeVectorDocument> documents = store.findByResumeIds(Set.of("201"));

        assertEquals("7", documents.get(0).metadata().get(META_USER_ID));
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }

    private static ObjectProvider<QdrantResumeVectorStore> emptyProvider() {
        return provider(null);
    }
}
