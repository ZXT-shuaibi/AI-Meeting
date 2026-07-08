package com.hewei.hzyjy.xunzhi.career.resume.rag;

import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import com.hewei.hzyjy.xunzhi.career.ai.EmbeddingGateway;
import com.hewei.hzyjy.xunzhi.career.config.CareerRagProperties;
import com.hewei.hzyjy.xunzhi.career.observability.AiToolExecutionEvent;
import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.CHUNK_TYPE_PROJECT;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_CHUNK_INDEX;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_CHUNK_TYPE;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_RESUME_ID;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResumeRagServiceTest {

    @Test
    void retrievesCandidatesFromIndependentBm25LaneWhenVectorSearchMisses() {
        ResumeVectorDocument javaProject = javaProject();
        ResumeVectorDocument unrelated = unrelatedProject();
        ResumeRagService service = new ResumeRagService(
                new ResumeChunker(),
                new StaticEmbeddingGateway(),
                new VectorMissStore(List.of(javaProject, unrelated)),
                request -> AiGatewayResult.builder().content("").provider("test").build(),
                new CareerRagProperties(),
                (query, candidates, limit) -> candidates.stream().limit(limit).toList(),
                emptyProvider(),
                emptyTraceProvider()
        );

        List<String> result = service.retrieveTemplates("Spring AI Redis Qdrant", 1, 7L, Set.of("101", "102"));

        assertFalse(result.isEmpty());
        assertEquals(javaProject.text(), result.get(0));
    }

    @Test
    void bm25LaneStillReturnsCandidatesWhenEmbeddingFails() {
        ResumeVectorDocument javaProject = javaProject();
        ResumeRagService service = new ResumeRagService(
                new ResumeChunker(),
                new FailingEmbeddingGateway(),
                new VectorMissStore(List.of(javaProject, unrelatedProject())),
                request -> AiGatewayResult.builder().content("").provider("test").build(),
                new CareerRagProperties(),
                (query, candidates, limit) -> candidates.stream().limit(limit).toList(),
                emptyProvider(),
                emptyTraceProvider()
        );

        List<String> result = service.retrieveTemplates("Spring AI Redis Qdrant", 1, 7L, Set.of("101", "102"));

        assertEquals(List.of(javaProject.text()), result);
    }


    @Test
    void storesTextOnlyChunksForBm25WhenEmbeddingFailsDuringIngestion() {
        InMemoryResumeVectorStore store = new InMemoryResumeVectorStore();
        ResumeRagService service = new ResumeRagService(
                new ResumeChunker(),
                new FailingEmbeddingGateway(),
                new ResumeVectorStore() {
                    @Override
                    public void addAll(List<ResumeVectorDocument> documents) {
                        store.addAll(documents);
                    }

                    @Override
                    public List<ResumeVectorMatch> search(float[] queryVector, Set<String> chunkTypes, Set<String> resumeIds, Map<String, String> metadataFilters, double minScore, int limit) {
                        return store.search(queryVector, chunkTypes, resumeIds, metadataFilters, minScore, limit);
                    }

                    @Override
                    public List<ResumeVectorDocument> findByResumeIds(Set<String> resumeIds) {
                        return store.findByResumeIds(resumeIds);
                    }
                },
                request -> AiGatewayResult.builder().content("").provider("test").build(),
                new CareerRagProperties(),
                (query, candidates, limit) -> candidates.stream().limit(limit).toList(),
                emptyProvider(),
                emptyTraceProvider()
        );

        service.storeCvBO(CvBO.builder()
                .id(201L)
                .userId(7L)
                .name("candidate")
                .summary("Spring AI LangChain4j Redis Qdrant resume optimization")
                .build());
        List<String> result = service.retrieveTemplates("Spring AI Redis Qdrant", 1, 7L, Set.of("201"));

        assertFalse(store.findByResumeIds(Set.of("201")).isEmpty());
        assertFalse(result.isEmpty());
    }

    @Test
    void publishesToolEventsForRagDegradationWhileReturningBm25Fallback() {
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
        ResumeVectorDocument javaProject = javaProject();
        ResumeRagService service = new ResumeRagService(
                new ResumeChunker(),
                new FailingEmbeddingGateway(),
                new VectorMissStore(List.of(javaProject, unrelatedProject())),
                request -> {
                    throw new IllegalStateException("llm unavailable");
                },
                new CareerRagProperties(),
                (query, candidates, limit) -> {
                    throw new IllegalStateException("rerank unavailable");
                },
                emptyProvider(),
                traceProvider(new AiTracePublisher(eventPublisher))
        );

        List<String> result = service.retrieveTemplates("Spring AI Redis Qdrant", 1, 7L, Set.of("101", "102"));

        assertEquals(List.of(javaProject.text()), result);
        List<String> failedTools = eventPublisher.toolEvents().stream()
                .filter(event -> !event.success())
                .map(AiToolExecutionEvent::toolName)
                .toList();
        assertEquals(List.of(
                "rag-hyde",
                "rag-multi-query",
                "rag-vector-coarse",
                "rag-vector-fine",
                "rag-rerank"
        ), failedTools);
    }

    private ResumeVectorDocument javaProject() {
        return ResumeVectorDocument.builder()
                .id("v1")
                .text("Project: Spring AI LangChain4j Redis Qdrant resume optimization")
                .vector(new float[]{1.0F})
                .metadata(Map.of(
                        META_RESUME_ID, "101",
                        META_USER_ID, "7",
                        META_CHUNK_TYPE, CHUNK_TYPE_PROJECT,
                        META_CHUNK_INDEX, "0"
                ))
                .build();
    }

    private ResumeVectorDocument unrelatedProject() {
        return ResumeVectorDocument.builder()
                .id("v2")
                .text("Project: React CSS portfolio animation")
                .vector(new float[]{0.0F})
                .metadata(Map.of(
                        META_RESUME_ID, "102",
                        META_USER_ID, "7",
                        META_CHUNK_TYPE, CHUNK_TYPE_PROJECT,
                        META_CHUNK_INDEX, "0"
                ))
                .build();
    }

    private static class StaticEmbeddingGateway implements EmbeddingGateway {
        @Override
        public float[] embed(String text) {
            return new float[]{1.0F};
        }

        @Override
        public List<float[]> embedAll(List<String> texts) {
            return texts.stream().map(text -> embed(text)).toList();
        }
    }

    private static class FailingEmbeddingGateway implements EmbeddingGateway {
        @Override
        public float[] embed(String text) {
            throw new IllegalStateException("embedding unavailable");
        }

        @Override
        public List<float[]> embedAll(List<String> texts) {
            throw new IllegalStateException("embedding unavailable");
        }
    }

    private record VectorMissStore(List<ResumeVectorDocument> documents) implements ResumeVectorStore {
        @Override
        public void addAll(List<ResumeVectorDocument> documents) {
        }

        @Override
        public List<ResumeVectorMatch> search(float[] queryVector, Set<String> chunkTypes, Set<String> resumeIds, Map<String, String> metadataFilters, double minScore, int limit) {
            return List.of();
        }

        @Override
        public List<ResumeVectorDocument> findByResumeIds(Set<String> resumeIds) {
            return documents.stream()
                    .filter(document -> resumeIds == null || resumeIds.isEmpty() || resumeIds.contains(document.metadata().get(META_RESUME_ID)))
                    .toList();
        }
    }

    private static ObjectProvider<StringRedisTemplate> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public StringRedisTemplate getObject(Object... args) {
                return null;
            }

            @Override
            public StringRedisTemplate getIfAvailable() {
                return null;
            }

            @Override
            public StringRedisTemplate getIfUnique() {
                return null;
            }

            @Override
            public StringRedisTemplate getObject() {
                return null;
            }
        };
    }

    private static ObjectProvider<AiTracePublisher> emptyTraceProvider() {
        return new ObjectProvider<>() {
            @Override
            public AiTracePublisher getObject(Object... args) {
                return null;
            }

            @Override
            public AiTracePublisher getIfAvailable() {
                return null;
            }

            @Override
            public AiTracePublisher getIfUnique() {
                return null;
            }

            @Override
            public AiTracePublisher getObject() {
                return null;
            }
        };
    }

    private static ObjectProvider<AiTracePublisher> traceProvider(AiTracePublisher publisher) {
        return new ObjectProvider<>() {
            @Override
            public AiTracePublisher getObject(Object... args) {
                return publisher;
            }

            @Override
            public AiTracePublisher getIfAvailable() {
                return publisher;
            }

            @Override
            public AiTracePublisher getIfUnique() {
                return publisher;
            }

            @Override
            public AiTracePublisher getObject() {
                return publisher;
            }
        };
    }

    private static class RecordingEventPublisher implements ApplicationEventPublisher {
        private final List<AiToolExecutionEvent> toolEvents = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            if (event instanceof AiToolExecutionEvent toolEvent) {
                toolEvents.add(toolEvent);
            }
        }

        List<AiToolExecutionEvent> toolEvents() {
            return toolEvents;
        }
    }
}
