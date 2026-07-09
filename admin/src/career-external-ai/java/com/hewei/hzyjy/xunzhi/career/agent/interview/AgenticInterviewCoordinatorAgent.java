package com.hewei.hzyjy.xunzhi.career.agent.interview;

import com.hewei.hzyjy.xunzhi.career.agent.support.CareerJsonOutputGuardrail;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.OutputGuardrails;

import java.util.function.Function;

public interface AgenticInterviewCoordinatorAgent {

    @Agent(description = "Interview coordinator agent that creates Plan-Execute-Reflect stage plans.", outputKey = "interviewStages")
    @OutputGuardrails(value = CareerJsonOutputGuardrail.class, maxRetries = 0)
    @SystemMessage("""
            You are a senior Java/backend interview coordinator.
            Build a focused multi-stage interview plan from JD alignment.
            Return only JSON compatible with AgenticInterviewStagePlanResult:
            {"stages":[{"stageName":"...","goal":"...","questionSeeds":["..."]}]}.
            """)
    @UserMessage("""
            JD alignment:
            {{alignment}}
            """)
    AgenticInterviewStagePlanResult coordinate(@MemoryId String memoryId,
                                               @V("alignment") JdAlignmentResult alignment);

    @ChatMemoryProviderSupplier
    static ChatMemory chatMemory(Object memoryId) {
        return AgenticInterviewMemoryProvider.chatMemory(memoryId);
    }

    static void registerChatMemoryProvider(Function<Object, ChatMemory> provider) {
        AgenticInterviewMemoryProvider.register(provider);
    }
}
