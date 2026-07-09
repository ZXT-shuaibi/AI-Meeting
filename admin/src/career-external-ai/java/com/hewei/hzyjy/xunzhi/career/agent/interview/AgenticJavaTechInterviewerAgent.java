package com.hewei.hzyjy.xunzhi.career.agent.interview;

import com.hewei.hzyjy.xunzhi.career.agent.support.CareerJsonOutputGuardrail;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.OutputGuardrails;

import java.util.List;
import java.util.function.Function;

public interface AgenticJavaTechInterviewerAgent {

    @Agent(description = "Java technical interviewer that contributes one planning-layer question suggestion only.", outputKey = "technicalQuestion")
    @OutputGuardrails(value = CareerJsonOutputGuardrail.class, maxRetries = 0)
    @SystemMessage("""
            You are a senior Java/backend interviewer.
            Generate exactly one first-round technical question for the planning layer.
            Do not execute, score, persist, or advance interview state. AI-Meeting owns execution, locks, idempotency and snapshots.
            Return only JSON compatible with TechnicalQuestionSuggestion:
            {"question":"...","rationale":"..."}.
            """)
    @UserMessage("""
            Java technical stage plan.

            Resume:
            {{cv}}

            JD:
            {{jobDescription}}

            JD alignment:
            {{alignment}}

            Stage candidates:
            {{stages}}

            Runtime probing skill:
            {{skillContext}}
            """)
    TechnicalQuestionSuggestion generateQuestion(@MemoryId String memoryId,
                                                 @V("cv") CvBO cv,
                                                 @V("jobDescription") String jobDescription,
                                                 @V("alignment") JdAlignmentResult alignment,
                                                 @V("stages") List<InterviewStagePlan> stages,
                                                 @V("skillContext") String skillContext);

    @ChatMemoryProviderSupplier
    static ChatMemory chatMemory(Object memoryId) {
        return AgenticInterviewMemoryProvider.chatMemory(memoryId);
    }

    static void registerChatMemoryProvider(Function<Object, ChatMemory> provider) {
        AgenticInterviewMemoryProvider.register(provider);
    }
}
