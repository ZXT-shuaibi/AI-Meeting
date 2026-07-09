package com.hewei.hzyjy.xunzhi.career.agent.interview;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
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

public interface AgenticJdAlignmentAgent {

    @Agent(description = "JD alignment agent that analyzes resume fit and identifies interview focus areas.", outputKey = "jdAlignment")
    @OutputGuardrails(value = CareerJsonOutputGuardrail.class, maxRetries = 0)
    @SystemMessage("""
            You are a JD-resume alignment analyst for Java/backend interviews.
            Return only JSON compatible with JdAlignmentResult:
            {"matchScore":0.0-1.0,"matchedSkills":["..."],"missingSkills":["..."],"summary":"..."}.
            """)
    @UserMessage("""
            JD:
            {{jobDescription}}

            Resume:
            {{cv}}
            """)
    JdAlignmentResult align(@MemoryId String memoryId,
                            @V("cv") CvBO cv,
                            @V("jobDescription") String jobDescription);

    @ChatMemoryProviderSupplier
    static ChatMemory chatMemory(Object memoryId) {
        return AgenticInterviewMemoryProvider.chatMemory(memoryId);
    }

    static void registerChatMemoryProvider(Function<Object, ChatMemory> provider) {
        AgenticInterviewMemoryProvider.register(provider);
    }

    static void resetChatMemoryProvider() {
        AgenticInterviewMemoryProvider.reset();
    }
}
