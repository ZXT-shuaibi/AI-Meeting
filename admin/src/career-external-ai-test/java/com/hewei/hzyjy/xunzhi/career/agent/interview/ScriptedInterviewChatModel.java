package com.hewei.hzyjy.xunzhi.career.agent.interview;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

class ScriptedInterviewChatModel implements ChatModel {

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        String prompt = String.valueOf(chatRequest.messages());
        if (prompt.contains("How did you use Redis?") || prompt.contains("cache aside")) {
            return response("{\"score\":5,\"decision\":\"PROBE\",\"feedback\":\"Agentic reflection asks for concrete metrics.\",\"probeSuggestions\":[\"Ask QPS and latency\",\"Ask failure fallback\"]}");
        }
        if (prompt.contains("Session id:") || prompt.contains("First question candidate:")) {
            return response("{\"sessionId\":\"s-agentic\",\"alignment\":{\"matchScore\":0.88,\"matchedSkills\":[\"Java\",\"Redis\"],\"missingSkills\":[\"Qdrant\"],\"summary\":\"Agentic JD alignment\"},\"stages\":[{\"stageName\":\"AGENTIC_JD_ALIGNMENT\",\"goal\":\"Validate JD fit\",\"questionSeeds\":[\"Java\",\"Redis\"]}],\"firstQuestion\":\"Agentic first question about Redis architecture\"}");
        }
        if (prompt.contains("JD alignment:")) {
            return response("{\"stages\":[{\"stageName\":\"AGENTIC_JD_ALIGNMENT\",\"goal\":\"Validate JD fit\",\"questionSeeds\":[\"Java\",\"Redis\"]},{\"stageName\":\"AGENTIC_TECH_DEPTH\",\"goal\":\"Probe implementation depth\",\"questionSeeds\":[\"cache\",\"metrics\"]}]}");
        }
        if (prompt.contains("JD:") && prompt.contains("Resume:")) {
            return response("{\"matchScore\":0.88,\"matchedSkills\":[\"Java\",\"Redis\"],\"missingSkills\":[\"Qdrant\"],\"summary\":\"Agentic JD alignment\"}");
        }
        return response("{}");
    }

    private ChatResponse response(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .build();
    }
}
