package com.hewei.hzyjy.xunzhi.career.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenAiCompatibleEmbeddingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OpenAiCompatibleEmbeddingConfiguration.class);

    @Test
    void createsEmbeddingModelWhenAllRequiredPropertiesArePresent() {
        contextRunner.withPropertyValues(
                "xunzhi-agent.embedding.enabled=true",
                "xunzhi-agent.embedding.base-url=https://embedding.example/v1",
                "xunzhi-agent.embedding.api-key=test-key",
                "xunzhi-agent.embedding.model=test-embedding"
        ).run(context -> assertThat(context).hasSingleBean(EmbeddingModel.class));
    }

    @Test
    void doesNotCreateEmbeddingModelWhenApiKeyIsMissing() {
        contextRunner.withPropertyValues(
                "xunzhi-agent.embedding.enabled=true",
                "xunzhi-agent.embedding.base-url=https://embedding.example/v1",
                "xunzhi-agent.embedding.model=test-embedding"
        ).run(context -> assertThat(context).doesNotHaveBean(EmbeddingModel.class));
    }

    @Test
    void createsConfiguredEmbeddingModelAlongsideAnExistingProvider() {
        contextRunner.withBean("existingEmbeddingModel", EmbeddingModel.class, () -> mock(EmbeddingModel.class))
                .withPropertyValues(
                        "xunzhi-agent.embedding.enabled=true",
                        "xunzhi-agent.embedding.base-url=https://embedding.example/v1",
                        "xunzhi-agent.embedding.api-key=test-key",
                        "xunzhi-agent.embedding.model=test-embedding"
                ).run(context -> {
                    assertThat(context).hasBean(OpenAiCompatibleEmbeddingConfiguration.EMBEDDING_MODEL_BEAN_NAME);
                    assertThat(context.getBeansOfType(EmbeddingModel.class)).hasSize(2);
                });
    }
}
