package com.hewei.hzyjy.xunzhi.agent.service.impl;

import com.hewei.hzyjy.xunzhi.agent.application.AgentResolver;
import com.hewei.hzyjy.xunzhi.agent.dao.entity.AgentPropertiesDO;
import com.hewei.hzyjy.xunzhi.agent.service.AgentConversationService;
import com.hewei.hzyjy.xunzhi.career.observability.AiInvocationFailedEvent;
import com.hewei.hzyjy.xunzhi.career.observability.AiInvocationStartedEvent;
import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import com.hewei.hzyjy.xunzhi.conversation.application.ConversationMessageHistoryService;
import com.hewei.hzyjy.xunzhi.conversation.application.ConversationMessagePersistenceService;
import com.hewei.hzyjy.xunzhi.conversation.application.ConversationOwnershipService;
import com.hewei.hzyjy.xunzhi.conversation.application.ConversationStreamingSupport;
import com.hewei.hzyjy.xunzhi.toolkit.xunfei.AgentPropertiesLoader;
import com.hewei.hzyjy.xunzhi.toolkit.xunfei.XingChenAIClient;
import com.hewei.hzyjy.xunzhi.user.api.io.req.UserMessageReqDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMessageServiceImplTraceTest {

    @Test
    void publishesInvocationTraceWhenXingChenAgentChatFails() throws Exception {
        XingChenAIClient xingChenAIClient = mock(XingChenAIClient.class);
        AgentResolver agentResolver = mock(AgentResolver.class);
        AgentPropertiesLoader agentPropertiesLoader = mock(AgentPropertiesLoader.class);
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
        ThreadPoolTaskExecutor executor = directExecutor();
        AgentMessageServiceImpl service = new AgentMessageServiceImpl(
                agentResolver,
                xingChenAIClient,
                agentPropertiesLoader,
                mock(AgentConversationService.class),
                mock(ConversationOwnershipService.class),
                mock(ConversationMessageHistoryService.class),
                mock(ConversationMessagePersistenceService.class),
                new ConversationStreamingSupport(),
                executor,
                traceProvider(new AiTracePublisher(eventPublisher))
        );
        AgentPropertiesDO agent = new AgentPropertiesDO();
        agent.setId(8L);
        agent.setAgentName("legacy-agent");
        agent.setApiKey("key");
        agent.setApiSecret("secret");
        agent.setApiFlowId("flow-id");
        when(agentResolver.resolveAgentId("session-1", null)).thenReturn(8L);
        when(agentPropertiesLoader.getByAgentId(8L)).thenReturn(agent);
        doThrow(new RuntimeException("xingchen unavailable")).when(xingChenAIClient).chat(
                eq("hello"),
                eq("session-1"),
                any(),
                eq(true),
                any(),
                any(),
                eq("key"),
                eq("secret"),
                eq("flow-id")
        );
        UserMessageReqDTO request = new UserMessageReqDTO();
        request.setSessionId("session-1");
        request.setInputMessage("hello");

        service.agentChatSse(request);
        executor.shutdown();

        assertTrue(eventPublisher.events().stream()
                .filter(AiInvocationStartedEvent.class::isInstance)
                .map(AiInvocationStartedEvent.class::cast)
                .anyMatch(event -> "LEGACY_XINGCHEN_AGENT_CHAT".equals(event.sceneCode())
                        && "session-1".equals(event.sessionId())));
        assertTrue(eventPublisher.events().stream()
                .filter(AiInvocationFailedEvent.class::isInstance)
                .map(AiInvocationFailedEvent.class::cast)
                .anyMatch(event -> "LEGACY_XINGCHEN_AGENT_CHAT".equals(event.sceneCode())
                        && "flow-id".equals(event.modelOrFlowId())
                        && event.errorMessage().contains("xingchen unavailable")));
    }

    private static ThreadPoolTaskExecutor directExecutor() {
        return new ThreadPoolTaskExecutor() {
            @Override
            public Future<?> submit(Runnable task) {
                task.run();
                return CompletableFuture.completedFuture(null);
            }
        };
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
