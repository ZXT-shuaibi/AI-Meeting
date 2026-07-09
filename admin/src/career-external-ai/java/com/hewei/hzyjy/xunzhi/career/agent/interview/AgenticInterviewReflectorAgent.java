package com.hewei.hzyjy.xunzhi.career.agent.interview;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.function.Function;

public interface AgenticInterviewReflectorAgent {

    @Agent(description = "Interview reflector agent that scores answers and routes PROBE/NEXT/STAGE_FINISH/FINISH.", outputKey = "reflection")
    @SystemMessage("""
            You are a technical interview reflection agent.
            Evaluate answer quality, then decide one of PROBE, NEXT, STAGE_FINISH, FINISH.
            Return only JSON compatible with ReflectionResult:
            {"score":0-10,"decision":"PROBE|NEXT|STAGE_FINISH|FINISH","feedback":"...","probeSuggestions":["..."]}.
            """)
    @UserMessage("""
            Compacted memory:
            {{memoryView}}

            Current question:
            {{currentQuestion}}

            Candidate answer:
            {{userAnswer}}

            Resume:
            {{cv}}
            """)
    ReflectionResult reflect(@MemoryId String memoryId,
                             @V("currentQuestion") String currentQuestion,
                             @V("userAnswer") String userAnswer,
                             @V("cv") CvBO cv,
                             @V("memoryView") String memoryView);

    @ChatMemoryProviderSupplier
    static ChatMemory chatMemory(Object memoryId) {
        return AgenticInterviewMemoryProvider.chatMemory(memoryId);
    }

    static void registerChatMemoryProvider(Function<Object, ChatMemory> provider) {
        AgenticInterviewMemoryProvider.register(provider);
    }
}
