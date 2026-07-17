package com.hewei.hzyjy.xunzhi.career.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class OpenAiCompatibleEmbeddingConfiguration {

    public static final String EMBEDDING_MODEL_BEAN_NAME = "configuredEmbeddingModel";

    @Bean(name = EMBEDDING_MODEL_BEAN_NAME)
    @Primary
    // Keep the RAG text fallback available when no external embedding provider is configured.
    @Conditional(EmbeddingConnectionConfiguredCondition.class)
    public EmbeddingModel embeddingModel(EmbeddingProperties properties) {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModel())
                .dimensions(properties.getDimensions())
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .maxRetries(1)
                .build();
    }

    static final class EmbeddingConnectionConfiguredCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return Boolean.parseBoolean(context.getEnvironment().getProperty("xunzhi-agent.embedding.enabled", "false"))
                    && hasText(context, "xunzhi-agent.embedding.base-url")
                    && hasText(context, "xunzhi-agent.embedding.api-key")
                    && hasText(context, "xunzhi-agent.embedding.model");
        }

        private boolean hasText(ConditionContext context, String propertyName) {
            return StringUtils.hasText(context.getEnvironment().getProperty(propertyName));
        }
    }
}
