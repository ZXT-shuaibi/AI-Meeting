package com.hewei.hzyjy.xunzhi.career.config;

import com.hewei.hzyjy.xunzhi.career.agent.interview.AgenticInterviewCoordinatorAgent;
import com.hewei.hzyjy.xunzhi.career.agent.interview.AgenticInterviewOrchestratorAgent;
import com.hewei.hzyjy.xunzhi.career.agent.interview.AgenticInterviewReflectorAgent;
import com.hewei.hzyjy.xunzhi.career.agent.interview.AgenticJdAlignmentAgent;
import com.hewei.hzyjy.xunzhi.career.ai.LangChain4jAgenticSafetyPolicy;
import com.hewei.hzyjy.xunzhi.career.memory.LangChain4jHybridMemoryAdapter;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.function.Function;

@Configuration
@EnableConfigurationProperties(XunzhiLangChain4jProperties.class)
@Import(LangChain4jAgenticSafetyPolicy.class)
@ConditionalOnBean(ChatModel.class)
@ConditionalOnProperty(prefix = "xunzhi-agent.langchain4j", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgenticInterviewRuntimeConfiguration {

    @Bean("AgenticJDAlignmentAgent")
    @ConditionalOnMissingBean(name = "AgenticJDAlignmentAgent")
    public AgenticJdAlignmentAgent agenticJdAlignmentAgent(
            ChatModel careerLangChain4jChatModel,
            ObjectProvider<LangChain4jHybridMemoryAdapter> memoryAdapterProvider,
            LangChain4jAgenticSafetyPolicy safetyPolicy) {
        safetyPolicy.assertNativeAgenticListenersDisabled();
        registerHybridChatMemory(memoryAdapterProvider.getIfAvailable());
        return AgenticServices.createAgenticSystem(AgenticJdAlignmentAgent.class, careerLangChain4jChatModel);
    }

    @Bean("AgenticInterviewCoordinatorAgent")
    @ConditionalOnMissingBean(name = "AgenticInterviewCoordinatorAgent")
    public AgenticInterviewCoordinatorAgent agenticInterviewCoordinatorAgent(
            ChatModel careerLangChain4jChatModel,
            LangChain4jAgenticSafetyPolicy safetyPolicy) {
        safetyPolicy.assertNativeAgenticListenersDisabled();
        return AgenticServices.createAgenticSystem(AgenticInterviewCoordinatorAgent.class, careerLangChain4jChatModel);
    }

    @Bean("AgenticInterviewReflectorAgent")
    @ConditionalOnMissingBean(name = "AgenticInterviewReflectorAgent")
    public AgenticInterviewReflectorAgent agenticInterviewReflectorAgent(
            ChatModel careerLangChain4jChatModel,
            LangChain4jAgenticSafetyPolicy safetyPolicy) {
        safetyPolicy.assertNativeAgenticListenersDisabled();
        return AgenticServices.createAgenticSystem(AgenticInterviewReflectorAgent.class, careerLangChain4jChatModel);
    }

    @Bean("AgenticInterviewOrchestratorService")
    @ConditionalOnMissingBean(name = "AgenticInterviewOrchestratorService")
    public AgenticInterviewOrchestratorAgent agenticInterviewOrchestratorAgent(
            ChatModel careerLangChain4jChatModel,
            LangChain4jAgenticSafetyPolicy safetyPolicy) {
        safetyPolicy.assertNativeAgenticListenersDisabled();
        return AgenticServices.createAgenticSystem(AgenticInterviewOrchestratorAgent.class, careerLangChain4jChatModel);
    }

    private void registerHybridChatMemory(LangChain4jHybridMemoryAdapter memoryAdapter) {
        if (memoryAdapter == null || !memoryAdapter.isLangChain4jMemoryAvailable()) {
            return;
        }
        Function<Object, ChatMemory> provider = memoryId -> (ChatMemory) memoryAdapter.chatMemory(memoryId);
        AgenticJdAlignmentAgent.registerChatMemoryProvider(provider);
    }
}
