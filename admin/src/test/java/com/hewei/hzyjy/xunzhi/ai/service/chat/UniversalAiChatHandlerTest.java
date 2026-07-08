package com.hewei.hzyjy.xunzhi.ai.service.chat;

import com.hewei.hzyjy.xunzhi.ai.dao.entity.AiPropertiesDO;
import com.hewei.hzyjy.xunzhi.career.observability.AiInvocationFailedEvent;
import com.hewei.hzyjy.xunzhi.career.observability.AiInvocationStartedEvent;
import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import com.hewei.hzyjy.xunzhi.toolkit.xunfei.AIContentAccumulator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalAiChatHandlerTest {

    @Test
    void publishesUnifiedTraceWhenLegacySpringAiChatFailsBeforeStreaming() {
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
        UniversalAiChatHandler handler = new UniversalAiChatHandler(traceProvider(new AiTracePublisher(eventPublisher)));
        AiPropertiesDO properties = new AiPropertiesDO();
        properties.setAiName("legacy-interview-evaluator");
        properties.setAiType("openai");
        properties.setModelName("gpt-test");

        assertThrows(Exception.class, () -> handler.streamToSink(
                properties,
                "answer text",
                List.of(),
                null,
                new AIContentAccumulator()
        ));

        assertTrue(eventPublisher.events().stream().anyMatch(AiInvocationStartedEvent.class::isInstance));
        assertTrue(eventPublisher.events().stream().anyMatch(AiInvocationFailedEvent.class::isInstance));
    }

    private static ObjectProvider<AiTracePublisher> traceProvider(AiTracePublisher publisher) {
        return new ObjectProvider<>() {
            @Override
            public AiTracePublisher getObject(Object... args) {
                return publisher;
            }

            @Override
            public AiTracePublisher getIfAvailable() {
                return publisher;
            }

            @Override
            public AiTracePublisher getIfUnique() {
                return publisher;
            }

            @Override
            public AiTracePublisher getObject() {
                return publisher;
            }
        };
    }

    private static class RecordingEventPublisher implements ApplicationEventPublisher {
        private final List<Object> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }

        List<Object> events() {
            return events;
        }
    }
}
