package com.hewei.hzyjy.xunzhi.career.resume.rag;

import com.hewei.hzyjy.xunzhi.career.config.XunzhiLangChain4jProperties;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_CHUNK_INDEX;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_CHUNK_TYPE;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_RESUME_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QdrantResumeVectorStoreTest {

    @Test
    void rejectsVectorsThatDoNotMatchConfiguredDimension() {
        XunzhiLangChain4jProperties properties = new XunzhiLangChain4jProperties();
        properties.setEnabled(true);
        properties.getQdrant().setEnabled(true);
        properties.getQdrant().setCollectionName("career_resume_test");
        properties.getQdrant().setHost("localhost");
        properties.getQdrant().setVectorSize(3);
        QdrantResumeVectorStore store = new QdrantResumeVectorStore(properties);

        ResumeVectorDocument document = ResumeVectorDocument.builder()
                .id("vector-1")
                .text("resume")
                .vector(new float[]{0.1F, 0.2F})
                .metadata(Map.of(META_RESUME_ID, "resume-1"))
                .build();

        assertThrows(IllegalArgumentException.class, () -> store.addAll(List.of(document)));
    }

    @Test
    void remainsAvailableWhenLangChain4jChatRuntimeIsDisabled() {
        XunzhiLangChain4jProperties properties = new XunzhiLangChain4jProperties();
        properties.setEnabled(false);
        properties.getQdrant().setEnabled(true);
        properties.getQdrant().setCollectionName("career_resume_test");
        properties.getQdrant().setHost("localhost");

        assertTrue(new QdrantResumeVectorStore(properties).available());
    }

    @Test
    void replacesExistingResumeVectorsBeforeUpsert() {
        XunzhiLangChain4jProperties properties = new XunzhiLangChain4jProperties();
        properties.setEnabled(true);
        properties.getQdrant().setEnabled(true);
        properties.getQdrant().setCollectionName("career_resume_test");
        properties.getQdrant().setHost("localhost");
        properties.getQdrant().setVectorSize(2);
        QdrantResumeVectorStore store = new QdrantResumeVectorStore(properties);
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("result", Map.of())));
        ReflectionTestUtils.setField(store, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(store, "collectionEnsured", true);

        ResumeVectorDocument document = ResumeVectorDocument.builder()
                .id("vector-1")
                .text("Spring AI LangChain4j Redis resume optimization")
                .vector(new float[]{0.1F, 0.2F})
                .metadata(Map.of(
                        META_RESUME_ID, "resume-1",
                        META_CHUNK_TYPE, ResumeRagConstants.CHUNK_TYPE_OVERVIEW,
                        META_CHUNK_INDEX, "0"
                ))
                .build();

        store.addAll(List.of(document));

        InOrder inOrder = inOrder(restTemplate);
        inOrder.verify(restTemplate).exchange(
                eq("http://localhost:6333/collections/career_resume_test/points/delete?wait=true"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        );
        inOrder.verify(restTemplate).exchange(
                eq("http://localhost:6333/collections/career_resume_test/points?wait=true"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Map.class)
        );
    }
}
