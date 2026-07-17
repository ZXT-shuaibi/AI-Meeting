package com.hewei.hzyjy.xunzhi.career.ai;

import com.hewei.hzyjy.xunzhi.career.config.XunzhiLangChain4jProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangChain4jEmbeddingAdapterTest {

    @Test
    void hashFallbackUsesConfiguredQdrantVectorSize() {
        XunzhiLangChain4jProperties properties = new XunzhiLangChain4jProperties();
        properties.getQdrant().setVectorSize(1024);
        LangChain4jEmbeddingAdapter adapter = new LangChain4jEmbeddingAdapter(new StaticApplicationContext(), properties);

        float[] vector = adapter.embed("Spring AI LangChain4j Redis RAG");

        assertEquals(1024, vector.length);
    }

    @Test
    void usesRegisteredEmbeddingModelForRemoteVectors() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("embeddingModel", new RecordingEmbeddingModel());
        LangChain4jEmbeddingAdapter adapter = new LangChain4jEmbeddingAdapter(context, new XunzhiLangChain4jProperties());

        float[] vector = adapter.embed("Resume retrieval");

        assertEquals(2, vector.length);
        assertEquals(0.6f, vector[0]);
        assertEquals(0.8f, vector[1]);
    }

    @Test
    void fallsBackWhenMultipleUnqualifiedEmbeddingModelsAreRegistered() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("firstEmbeddingModel", new RecordingEmbeddingModel());
        context.getBeanFactory().registerSingleton("secondEmbeddingModel", new RecordingEmbeddingModel());
        XunzhiLangChain4jProperties properties = new XunzhiLangChain4jProperties();
        properties.getQdrant().setVectorSize(1024);
        LangChain4jEmbeddingAdapter adapter = new LangChain4jEmbeddingAdapter(context, properties);

        float[] vector = adapter.embed("Resume retrieval");

        assertEquals(1024, vector.length);
    }

    private static final class RecordingEmbeddingModel implements EmbeddingModel {

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            assertTrue(segments.stream().allMatch(TextSegment.class::isInstance));
            return Response.from(List.of(Embedding.from(new float[]{0.6f, 0.8f})));
        }
    }
}
