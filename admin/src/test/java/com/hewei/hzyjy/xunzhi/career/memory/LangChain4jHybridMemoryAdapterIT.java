package com.hewei.hzyjy.xunzhi.career.memory;

import com.hewei.hzyjy.xunzhi.career.ai.AiGatewayResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangChain4jHybridMemoryAdapterIT {

    @Test
    void exposesHybridMemoryAsLangChain4jChatMemoryWithoutLeakingTypesToBusinessLayer() {
        HybridCompactingChatMemory hybridMemory = new HybridCompactingChatMemory(
                request -> AiGatewayResult.builder().content("compressed-low-history").build(),
                new InterviewRuleBasedScorer(),
                new DecisionIndex()
        );
        String memoryId = "interview:u1:s1";
        for (int i = 0; i < 31; i++) {
            hybridMemory.add(memoryId, MemoryMessage.builder()
                    .role(MemoryRole.USER)
                    .content("low " + i)
                    .build());
        }
        hybridMemory.add(memoryId, MemoryMessage.builder()
                .role(MemoryRole.ASSISTANT)
                .content("Reflect decision: NEXT, score=8, keep this pinned")
                .metadata(Map.of("scene", "INTERVIEW_REFLECTION"))
                .build());

        LangChain4jHybridMemoryAdapter adapter = new LangChain4jHybridMemoryAdapter(hybridMemory);

        Object bridged = adapter.chatMemory(memoryId);

        assertInstanceOf(classForName("dev.langchain4j.memory.ChatMemory"), bridged);
        assertEquals(memoryId, invoke(bridged, "id"));
        List<?> messages = list(invoke(bridged, "messages"));
        assertTrue(messages.stream().anyMatch(message -> "SYSTEM".equals(typeName(message))
                && text(message).contains("compressed-low-history")));
        assertTrue(messages.stream().anyMatch(message -> "SYSTEM".equals(typeName(message))
                && text(message).contains("Historical Key Decisions")));
        assertTrue(messages.stream().anyMatch(message -> "AI".equals(typeName(message))
                && text(message).contains("Reflect decision: NEXT")));
        assertTrue(messages.stream().anyMatch(message -> "USER".equals(typeName(message))
                && text(message).contains("low 30")));
    }

    @Test
    void updatesHybridMemoryThroughLangChain4jChatMemoryStore() {
        HybridCompactingChatMemory hybridMemory = new HybridCompactingChatMemory(
                request -> AiGatewayResult.builder().content("summary").build(),
                new InterviewRuleBasedScorer(),
                new DecisionIndex()
        );
        LangChain4jHybridMemoryAdapter adapter = new LangChain4jHybridMemoryAdapter(hybridMemory);
        Object store = adapter.chatMemoryStore();
        assertInstanceOf(classForName("dev.langchain4j.store.memory.chat.ChatMemoryStore"), store);

        invoke(store, "updateMessages", "resume:9", List.of(
                newMessage("dev.langchain4j.data.message.SystemMessage", "system context"),
                newMessage("dev.langchain4j.data.message.UserMessage", "candidate answer"),
                newMessage("dev.langchain4j.data.message.AiMessage", "Reflect decision: PROBE")
        ));

        List<MemoryMessage> messages = hybridMemory.messages("resume:9");
        assertEquals(3, messages.size());
        assertEquals(MemoryRole.SYSTEM, messages.get(0).role());
        assertEquals(MemoryRole.USER, messages.get(1).role());
        assertEquals(MemoryRole.ASSISTANT, messages.get(2).role());
        assertTrue(hybridMemory.view("resume:9", 3).decisionContext().contains("Reflect decision: PROBE"));

        List<?> langChainMessages = list(invoke(store, "getMessages", "resume:9"));
        assertEquals("SYSTEM", typeName(langChainMessages.get(0)));
        assertTrue(text(langChainMessages.get(0)).contains("Historical Key Decisions"));
        assertTrue(langChainMessages.stream().anyMatch(message -> "USER".equals(typeName(message))));
        assertTrue(langChainMessages.stream().anyMatch(message -> "AI".equals(typeName(message))));
    }

    private static Class<?> classForName(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Expected LangChain4j test dependency on classpath: " + className, ex);
        }
    }

    private static Object newMessage(String className, String text) {
        try {
            return classForName(className).getMethod("from", String.class).invoke(null, text);
        } catch (Exception ex) {
            throw new AssertionError("Failed to create LangChain4j message: " + className, ex);
        }
    }

    private static Object invoke(Object target, String methodName, Object... args) {
        try {
            Method method = findMethod(target.getClass(), methodName, args.length);
            return method.invoke(target, args);
        } catch (Exception ex) {
            throw new AssertionError("Failed to invoke method: " + methodName, ex);
        }
    }

    private static Method findMethod(Class<?> type, String methodName, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new AssertionError("Method not found: " + type.getName() + "." + methodName);
    }

    private static List<?> list(Object value) {
        assertInstanceOf(List.class, value);
        return (List<?>) value;
    }

    private static String typeName(Object message) {
        Object type = invoke(message, "type");
        return type == null ? "" : String.valueOf(type);
    }

    private static String text(Object message) {
        for (String methodName : List.of("text", "singleText")) {
            try {
                Object value = invoke(message, methodName);
                return value == null ? "" : String.valueOf(value);
            } catch (AssertionError ignored) {
                // Try the next LangChain4j text accessor.
            }
        }
        return String.valueOf(message);
    }
}
