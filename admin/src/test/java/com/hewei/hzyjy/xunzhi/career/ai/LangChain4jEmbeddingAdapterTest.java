package com.hewei.hzyjy.xunzhi.career.ai;

import com.hewei.hzyjy.xunzhi.career.config.XunzhiLangChain4jProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LangChain4jEmbeddingAdapterTest {

    @Test
    void hashFallbackUsesConfiguredQdrantVectorSize() {
        XunzhiLangChain4jProperties properties = new XunzhiLangChain4jProperties();
        properties.getQdrant().setVectorSize(1024);
        LangChain4jEmbeddingAdapter adapter = new LangChain4jEmbeddingAdapter(new StaticApplicationContext(), properties);

        float[] vector = adapter.embed("Spring AI LangChain4j Redis RAG");

        assertEquals(1024, vector.length);
    }
}