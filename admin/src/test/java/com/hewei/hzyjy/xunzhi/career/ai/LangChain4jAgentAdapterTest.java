package com.hewei.hzyjy.xunzhi.career.ai;

import com.hewei.hzyjy.xunzhi.career.observability.AiInvocationFailedEvent;
import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.support.StaticApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void invokesJavaTechInterviewerWithDeterministicArgumentOrder() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("JavaTechInterviewerAgent", JavaTechAgent.class);
        LangChain4jAgentAdapter adapter = new LangChain4jAgentAdapter(context, mock(ObjectProvider.class));

        String result = adapter.invoke("JavaTechInterviewerAgent", "generateQuestion", Map.of(
                "memoryId", "interview:100:s1",
                "cv", "cv-object",
                "jobDescription", "Java Redis JD",
                "alignment", "alignment-object",
                "stages", "stage-list",
                "skillContext", "question skill"
        ), String.class);

        assertEquals("interview:100:s1|cv-object|Java Redis JD|alignment-object|stage-list|question skill", result);
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

    @Test
    void unwrapsAgenticInvocationFailuresBeforePublishingTrace() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("AgenticInterviewReflectorAgent", FailingReflectAgent.class);
        CapturingPublisher publisher = new CapturingPublisher();
        ObjectProvider<AiTracePublisher> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(new AiTracePublisher(publisher));
        LangChain4jAgentAdapter adapter = new LangChain4jAgentAdapter(context, provider);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> adapter.invoke(
                "InterviewReflectorAgent",
                "reflect",
                Map.of(
                        "memoryId", "interview:100:s1",
                        "currentQuestion", "How did you use Redis?",
                        "userAnswer", "I used Redis.",
                        "cv", "cv-object",
                        "memoryView", "memory-view"
                ),
                String.class
        ));

        assertThat(thrown).hasRootCauseInstanceOf(NullPointerException.class);
        assertThat(publisher.events)
                .filteredOn(AiInvocationFailedEvent.class::isInstance)
                .singleElement()
                .satisfies(event -> assertThat(((AiInvocationFailedEvent) event).errorMessage())
                        .contains("LangChain4jManaged.current"));
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

    public static class FailingReflectAgent {
        public String reflect(String memoryId, String currentQuestion, String userAnswer, Object cv, Object memoryView) {
            throw new NullPointerException("LangChain4jManaged.current() returned null");
        }
    }

    public static class JavaTechAgent {
        public String generateQuestion(String memoryId, Object cv, String jobDescription, Object alignment, Object stages, String skillContext) {
            return String.join("|", List.of(memoryId, String.valueOf(cv), jobDescription, String.valueOf(alignment), String.valueOf(stages), skillContext));
        }
    }

    private static final class CapturingPublisher implements ApplicationEventPublisher {
        private final List<Object> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }

        @Override
        public void publishEvent(ApplicationEvent event) {
            events.add(event);
        }
    }
}
