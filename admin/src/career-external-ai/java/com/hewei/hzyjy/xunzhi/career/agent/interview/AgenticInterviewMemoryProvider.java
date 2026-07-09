package com.hewei.hzyjy.xunzhi.career.agent.interview;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.util.function.Function;

final class AgenticInterviewMemoryProvider {

    private static final int FALLBACK_MAX_MESSAGES = 16;
    private static volatile Function<Object, ChatMemory> provider =
            memoryId -> MessageWindowChatMemory.withMaxMessages(FALLBACK_MAX_MESSAGES);

    private AgenticInterviewMemoryProvider() {
    }

    static ChatMemory chatMemory(Object memoryId) {
        return provider.apply(memoryId);
    }

    static void register(Function<Object, ChatMemory> nextProvider) {
        provider = nextProvider == null
                ? memoryId -> MessageWindowChatMemory.withMaxMessages(FALLBACK_MAX_MESSAGES)
                : nextProvider;
    }

    static void reset() {
        register(null);
    }
}
