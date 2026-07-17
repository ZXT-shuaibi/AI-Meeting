package com.hewei.hzyjy.xunzhi.career.config;

import com.hewei.hzyjy.xunzhi.career.agent.cv.AgenticCvOptimizationAgent;
import com.hewei.hzyjy.xunzhi.career.agent.cv.AgenticCvOptimizationOrchestrator;
import com.hewei.hzyjy.xunzhi.career.agent.cv.AgenticCvOptimizationRuntime;
import com.hewei.hzyjy.xunzhi.career.agent.cv.CvReviewer;
import com.hewei.hzyjy.xunzhi.career.agent.cv.ScoredCvTailor;
import com.hewei.hzyjy.xunzhi.career.ai.LangChain4jAgenticSafetyPolicy;
import com.hewei.hzyjy.xunzhi.career.memory.LangChain4jHybridMemoryAdapter;
import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.function.Function;

@Configuration
@EnableConfigurationProperties(XunzhiLangChain4jProperties.class)
@Import(LangChain4jAgenticSafetyPolicy.class)
@ConditionalOnProperty(prefix = "xunzhi-agent.langchain4j", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgenticCvRuntimeConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ChatModel careerLangChain4jChatModel(XunzhiLangChain4jProperties properties) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("xunzhi-agent.langchain4j.api-key is required when LangChain4j CV runtime is enabled");
        }
        return OpenAiChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getChatModel())
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .maxRetries(1)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgenticCvOptimizationAgent agenticCvOptimizationAgent(
            ChatModel careerLangChain4jChatModel,
            ObjectProvider<LangChain4jHybridMemoryAdapter> memoryAdapterProvider,
            LangChain4jAgenticSafetyPolicy safetyPolicy) {
        safetyPolicy.assertNativeAgenticListenersDisabled();
        registerHybridChatMemory(memoryAdapterProvider.getIfAvailable());
        return AgenticServices.createAgenticSystem(AgenticCvOptimizationAgent.class, careerLangChain4jChatModel);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgenticCvOptimizationRuntime agenticCvOptimizationRuntime(
            AgenticCvOptimizationAgent agenticCvOptimizationAgent,
            ObjectProvider<AiTracePublisher> tracePublisherProvider,
            ObjectProvider<CareerOptimizationProperties> optimizationPropertiesProvider) {
        return new AgenticCvOptimizationRuntime(
                agenticCvOptimizationAgent,
                tracePublisherProvider.getIfAvailable(),
                optimizationPropertiesProvider.getIfAvailable(CareerOptimizationProperties::new)
        );
    }

    @Bean
    @Primary
    public AgenticCvOptimizationOrchestrator agenticCvOptimizationOrchestrator(
            CvReviewer reviewer,
            ScoredCvTailor tailor,
            AgenticCvOptimizationRuntime runtime) {
        return new AgenticCvOptimizationOrchestrator(reviewer, tailor, runtime);
    }

    private void registerHybridChatMemory(LangChain4jHybridMemoryAdapter memoryAdapter) {
        if (memoryAdapter == null || !memoryAdapter.isLangChain4jMemoryAvailable()) {
            return;
        }
        Function<Object, ChatMemory> provider = memoryId -> (ChatMemory) memoryAdapter.chatMemory(memoryId);
        AgenticCvOptimizationAgent.registerChatMemoryProvider(provider);
    }
}
