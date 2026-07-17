package com.hewei.hzyjy.xunzhi.career.ai;

import com.hewei.hzyjy.xunzhi.career.config.XunzhiLangChain4jProperties;
import com.hewei.hzyjy.xunzhi.career.config.OpenAiCompatibleEmbeddingConfiguration;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LangChain4jEmbeddingAdapter implements EmbeddingGateway {

    private static final int FALLBACK_DIMENSION = 256;

    private final ApplicationContext applicationContext;
    private final XunzhiLangChain4jProperties properties;

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        EmbeddingModel embeddingModel = resolveLangChain4jEmbeddingModel();
        if (embeddingModel != null) {
            try {
                return invokeLangChain4jEmbeddingModel(embeddingModel, texts);
            } catch (Exception ex) {
                log.warn("LangChain4j embedding model failed, using hashing fallback", ex);
            }
        }
        return texts.stream().map(this::hashEmbedding).toList();
    }

    private EmbeddingModel resolveLangChain4jEmbeddingModel() {
        if (applicationContext.containsBean(OpenAiCompatibleEmbeddingConfiguration.EMBEDDING_MODEL_BEAN_NAME)) {
            return applicationContext.getBean(
                    OpenAiCompatibleEmbeddingConfiguration.EMBEDDING_MODEL_BEAN_NAME,
                    EmbeddingModel.class
            );
        }
        return applicationContext.getBeanProvider(EmbeddingModel.class).getIfUnique();
    }

    private List<float[]> invokeLangChain4jEmbeddingModel(EmbeddingModel embeddingModel, List<String> texts) {
        return embeddingModel.embedAll(texts.stream().map(TextSegment::from).toList())
                .content().stream().map(Embedding::vector).toList();
    }

    private float[] hashEmbedding(String text) {
        float[] vector = new float[fallbackDimension()];
        String safeText = text == null ? "" : text.toLowerCase();
        for (String token : safeText.split("[^\\p{IsHan}\\p{Alnum}]+")) {
            if (token.isBlank()) {
                continue;
            }
            int hash = hash(token);
            int index = Math.floorMod(hash, vector.length);
            vector[index] += 1.0f;
        }
        normalize(vector);
        return vector;
    }

    private int fallbackDimension() {
        if (properties == null || properties.getQdrant() == null) {
            return FALLBACK_DIMENSION;
        }
        return Math.max(FALLBACK_DIMENSION, properties.getQdrant().getVectorSize());
    }

    private int hash(String token) {
        int hash = 0;
        for (byte b : token.getBytes(StandardCharsets.UTF_8)) {
            hash = 31 * hash + b;
        }
        return hash;
    }

    private void normalize(float[] vector) {
        double sum = 0.0;
        for (float v : vector) {
            sum += v * v;
        }
        if (sum == 0.0) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
    }
}
