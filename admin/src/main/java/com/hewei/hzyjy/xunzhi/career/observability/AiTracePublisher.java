package com.hewei.hzyjy.xunzhi.career.observability;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiTracePublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void started(String traceId, String sceneCode, String sessionId, String provider, String modelOrFlowId, String input) {
        eventPublisher.publishEvent(new AiInvocationStartedEvent(
                traceId,
                sessionId,
                null,
                null,
                sceneCode,
                sceneCode,
                sceneCode,
                provider,
                modelOrFlowId,
                Instant.now(),
                abbreviate(input, 1000),
                null,
                false,
                Map.of()
        ));
    }

    public void completed(String traceId, String sceneCode, String sessionId, String provider, String modelOrFlowId, long startMillis, String output) {
        Instant end = Instant.now();
        eventPublisher.publishEvent(new AiInvocationCompletedEvent(
                traceId,
                sessionId,
                null,
                null,
                sceneCode,
                sceneCode,
                sceneCode,
                provider,
                modelOrFlowId,
                Instant.ofEpochMilli(startMillis),
                end,
                Math.max(0, end.toEpochMilli() - startMillis),
                null,
                abbreviate(output, 2000),
                null,
                false,
                Map.of()
        ));
    }


    public void completed(String traceId, String sceneCode, String sessionId, String provider, String modelOrFlowId, long startMillis, String output, Map<String, Object> metadata) {
        Instant end = Instant.now();
        eventPublisher.publishEvent(new AiInvocationCompletedEvent(
                traceId,
                sessionId,
                null,
                null,
                sceneCode,
                sceneCode,
                sceneCode,
                provider,
                modelOrFlowId,
                Instant.ofEpochMilli(startMillis),
                end,
                Math.max(0, end.toEpochMilli() - startMillis),
                null,
                abbreviate(output, 2000),
                null,
                false,
                metadata == null ? Map.of() : metadata
        ));
    }
    public void failed(String traceId, String sceneCode, String sessionId, String provider, String modelOrFlowId, long startMillis, Throwable error) {
        Instant end = Instant.now();
        eventPublisher.publishEvent(new AiInvocationFailedEvent(
                traceId,
                sessionId,
                null,
                null,
                sceneCode,
                sceneCode,
                sceneCode,
                provider,
                modelOrFlowId,
                Instant.ofEpochMilli(startMillis),
                end,
                Math.max(0, end.toEpochMilli() - startMillis),
                null,
                error == null ? null : error.getMessage(),
                stackTrace(error),
                null,
                false,
                Map.of()
        ));
    }

    public void tool(AiToolExecutionEvent event) {
        eventPublisher.publishEvent(event);
    }

    private String abbreviate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private String stackTrace(Throwable error) {
        if (error == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return abbreviate(writer.toString(), 4000);
    }
}
