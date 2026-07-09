package com.hewei.hzyjy.xunzhi.career.agent.cv;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.concurrent.atomic.AtomicInteger;

class ScriptedCvChatModel implements ChatModel {

    private final AtomicInteger reviewCalls = new AtomicInteger();

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        String prompt = String.valueOf(chatRequest.messages());
        if (prompt.contains("简历定制专家") || prompt.contains("resume tailoring expert")) {
            return response("{\"name\":\"candidate\",\"summary\":\"Java backend Redis project\\nOptimized by Agentic tailor\",\"title\":\"Java Backend Engineer\",\"advice\":\"Tailored to JD\"}");
        }
        int call = reviewCalls.incrementAndGet();
        if (call == 1) {
            return response("{\"score\":0.72,\"feedback\":\"score 0.72, add quantified backend impact\"}");
        }
        return response("{\"score\":0.86,\"feedback\":\"score 0.86, ready for interview\"}");
    }

    private ChatResponse response(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .build();
    }
}
