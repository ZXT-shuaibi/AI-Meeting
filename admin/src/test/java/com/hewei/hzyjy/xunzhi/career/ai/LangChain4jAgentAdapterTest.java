package com.hewei.hzyjy.xunzhi.career.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.support.StaticApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class LangChain4jAgentAdapterTest {

    @Test
    void invokesKnownAgentMethodsWithDeterministicArgumentOrder() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("InterviewReflectorAgent", ReflectAgent.class);
        LangChain4jAgentAdapter adapter = new LangChain4jAgentAdapter(context, mock(ObjectProvider.class));

        String result = adapter.invoke("InterviewReflectorAgent", "reflect", Map.of(
                "memoryId", "interview:100:s1",
                "currentQuestion", "How did you use Redis?",
                "userAnswer", "I used Redis for cache aside with metrics.",
                "cv", "cv-object",
                "memoryView", "memory-view"
        ), String.class);

        assertEquals("interview:100:s1|How did you use Redis?|I used Redis for cache aside with metrics.|cv-object|memory-view", result);
        context.close();
    }

    @Test
    void prefersAgenticBeanWhenBothAgenticAndFallbackBeansExist() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("InterviewReflectorAgent", FallbackReflectAgent.class);
        context.registerSingleton("AgenticInterviewReflectorAgent", ReflectAgent.class);
        LangChain4jAgentAdapter adapter = new LangChain4jAgentAdapter(context, mock(ObjectProvider.class));

        String result = adapter.invoke("InterviewReflectorAgent", "reflect", Map.of(
                "memoryId", "interview:100:s1",
                "currentQuestion", "How did you use Redis?",
                "userAnswer", "I used Redis for cache aside with metrics.",
                "cv", "cv-object",
                "memoryView", "memory-view"
        ), String.class);

        assertEquals("interview:100:s1|How did you use Redis?|I used Redis for cache aside with metrics.|cv-object|memory-view", result);
        context.close();
    }

    public static class ReflectAgent {
        public String reflect(String memoryId, String currentQuestion, String userAnswer, Object cv, Object memoryView) {
            return String.join("|", List.of(memoryId, currentQuestion, userAnswer, String.valueOf(cv), String.valueOf(memoryView)));
        }
    }

    public static class FallbackReflectAgent {
        public String reflect(String memoryId, String currentQuestion, String userAnswer, Object cv, Object memoryView) {
            return "fallback";
        }
    }
}
