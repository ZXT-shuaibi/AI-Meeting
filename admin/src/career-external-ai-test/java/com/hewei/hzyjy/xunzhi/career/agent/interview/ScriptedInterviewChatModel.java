package com.hewei.hzyjy.xunzhi.career.agent.interview;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.Locale;

class ScriptedInterviewChatModel implements ChatModel {

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        String prompt = String.valueOf(chatRequest.messages());
        String current = latestMessage(chatRequest);
        String lowerPrompt = prompt.toLowerCase(Locale.ROOT);
        if (prompt.contains("ReflectionResult")
                || prompt.contains("Evaluate answer quality")
                || prompt.contains("PROBE|NEXT")
                || lowerPrompt.contains("technical interview reflection agent")
                || current.contains("Current question:")
                || current.contains("Candidate answer:")) {
            return response("{\"score\":5,\"decision\":\"PROBE\",\"feedback\":\"Agentic reflection asks for concrete metrics.\",\"probeSuggestions\":[\"Ask QPS and latency\",\"Ask failure fallback\"]}");
        }
        if (current.contains("Java technical stage plan")
                || current.contains("Runtime probing skill:")
                || prompt.contains("Generate exactly one first-round technical question")) {
            return response("{\"question\":\"Agentic JavaTech question about Redis hot key mitigation\",\"rationale\":\"Generated only for planning layer\"}");
        }
        if (current.contains("Session id:")
                || current.contains("First question candidate:")
                || prompt.contains("InterviewPlan")) {
            return response("{\"sessionId\":\"s-agentic\",\"alignment\":{\"matchScore\":0.88,\"matchedSkills\":[\"Java\",\"Redis\"],\"missingSkills\":[\"Qdrant\"],\"summary\":\"Agentic JD alignment\"},\"stages\":[{\"stageName\":\"AGENTIC_JD_ALIGNMENT\",\"goal\":\"Validate JD fit\",\"questionSeeds\":[\"Java\",\"Redis\"]}],\"firstQuestion\":\"Agentic first question about Redis architecture\"}");
        }
        if (current.contains("JD alignment:")
                || prompt.contains("AgenticInterviewStagePlanResult")
                || prompt.contains("multi-stage interview plan")
                || prompt.contains("interviewStages")) {
            return response("{\"stages\":[{\"stageName\":\"AGENTIC_JD_ALIGNMENT\",\"goal\":\"Validate JD fit\",\"questionSeeds\":[\"Java\",\"Redis\"]},{\"stageName\":\"AGENTIC_TECH_DEPTH\",\"goal\":\"Probe implementation depth\",\"questionSeeds\":[\"cache\",\"metrics\"]}]}");
        }
        if ((current.contains("JD:") && current.contains("Resume:"))
                || prompt.contains("JdAlignmentResult")
                || prompt.contains("JD-resume alignment analyst")) {
            return response("{\"matchScore\":0.88,\"matchedSkills\":[\"Java\",\"Redis\"],\"missingSkills\":[\"Qdrant\"],\"summary\":\"Agentic JD alignment\"}");
        }
        return response("{}");
    }

    private String latestMessage(ChatRequest chatRequest) {
        if (chatRequest.messages() == null || chatRequest.messages().isEmpty()) {
            return "";
        }
        return String.valueOf(chatRequest.messages().get(chatRequest.messages().size() - 1));
    }

    private ChatResponse response(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .build();
    }
}
