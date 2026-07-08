package com.hewei.hzyjy.xunzhi.career.ai;

public interface AiGateway {

    AiGatewayResult chat(AiPromptRequest request);

    default String summarize(String text) {
        return chat(AiPromptRequest.builder()
                .sceneCode("summary")
                .systemPrompt("Summarize the input while preserving facts and decisions.")
                .userPrompt(text)
                .build()).content();
    }
}
