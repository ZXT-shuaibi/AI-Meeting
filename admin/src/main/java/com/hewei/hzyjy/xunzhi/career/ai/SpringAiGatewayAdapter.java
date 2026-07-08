package com.hewei.hzyjy.xunzhi.career.ai;

import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpringAiGatewayAdapter implements AiGateway {

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final ObjectProvider<AiTracePublisher> tracePublisherProvider;

    @Override
    public AiGatewayResult chat(AiPromptRequest request) {
        String traceId = UUID.randomUUID().toString();
        AiTracePublisher tracePublisher = tracePublisherProvider.getIfAvailable();
        if (tracePublisher != null) {
            tracePublisher.started(traceId, request.sceneCode(), request.sessionId(), "spring-ai", null, request.userPrompt());
        }
        long start = System.currentTimeMillis();
        try {
            ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
            if (builder == null) {
                String fallback = localFallback(request);
                if (tracePublisher != null) {
                    tracePublisher.completed(traceId, request.sceneCode(), request.sessionId(), "spring-ai", null, start, fallback, Map.of("degraded", true, "reason", "ChatClient.Builder bean is unavailable"));
                }
                return AiGatewayResult.builder()
                        .content(fallback)
                        .provider("spring-ai")
                        .degraded(true)
                        .errorMessage("ChatClient.Builder bean is unavailable")
                        .build();
            }
            String content = builder.build()
                    .prompt()
                    .system(safe(request.systemPrompt()))
                    .user(safe(request.userPrompt()))
                    .call()
                    .content();
            if (tracePublisher != null) {
                tracePublisher.completed(traceId, request.sceneCode(), request.sessionId(), "spring-ai", null, start, content);
            }
            return AiGatewayResult.builder()
                    .content(content)
                    .provider("spring-ai")
                    .degraded(false)
                    .build();
        } catch (Exception ex) {
            log.warn("Spring AI gateway call failed, fallback will be used. scene={}", request.sceneCode(), ex);
            String fallback = localFallback(request);
            if (tracePublisher != null) {
                tracePublisher.failed(traceId, request.sceneCode(), request.sessionId(), "spring-ai", null, start, ex);
                tracePublisher.completed(traceId, request.sceneCode(), request.sessionId(), "spring-ai", null, start, fallback, Map.of("degraded", true, "fallbackAfterFailure", true, "error", ex.getMessage() == null ? "" : ex.getMessage()));
            }
            return AiGatewayResult.builder()
                    .content(fallback)
                    .provider("spring-ai")
                    .degraded(true)
                    .errorMessage(ex.getMessage())
                    .build();
        }
    }

    private String localFallback(AiPromptRequest request) {
        String prompt = safe(request.userPrompt());
        if (prompt.length() <= 800) {
            return prompt;
        }
        return prompt.substring(0, 800);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
