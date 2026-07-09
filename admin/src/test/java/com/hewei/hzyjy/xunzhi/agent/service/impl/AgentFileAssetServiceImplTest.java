package com.hewei.hzyjy.xunzhi.agent.service.impl;

import com.hewei.hzyjy.xunzhi.agent.application.AgentResolver;
import com.hewei.hzyjy.xunzhi.agent.application.BusinessAgentResolver;
import com.hewei.hzyjy.xunzhi.agent.application.BusinessAgentScene;
import com.hewei.hzyjy.xunzhi.agent.dao.entity.AgentPropertiesDO;
import com.hewei.hzyjy.xunzhi.career.observability.AiToolExecutionEvent;
import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import com.hewei.hzyjy.xunzhi.toolkit.xunfei.XingChenAIClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFileAssetServiceImplTest {

    @Test
    void publishesToolTraceWhenXingChenFileUploadFails() throws Exception {
        XingChenAIClient xingChenAIClient = mock(XingChenAIClient.class);
        AgentResolver agentResolver = mock(AgentResolver.class);
        BusinessAgentResolver businessAgentResolver = mock(BusinessAgentResolver.class);
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
        AgentFileAssetServiceImpl service = new AgentFileAssetServiceImpl(
                xingChenAIClient,
                agentResolver,
                businessAgentResolver,
                traceProvider(new AiTracePublisher(eventPublisher))
        );
        AgentPropertiesDO agent = new AgentPropertiesDO();
        agent.setId(9L);
        agent.setAgentName("general-agent-chat");
        agent.setApiKey("key");
        agent.setApiSecret("secret");
        agent.setApiFlowId("flow-id");
        when(businessAgentResolver.resolveRequired(BusinessAgentScene.GENERAL_AGENT_CHAT)).thenReturn(agent);
        when(xingChenAIClient.uploadFile(any(), eq("key"), eq("secret"))).thenThrow(new RuntimeException("upstream down"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                "PDF".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(ClientException.class, () -> service.uploadAndPersist(null, "resume", "tester", file));

        assertTrue(eventPublisher.events().stream()
                .filter(AiToolExecutionEvent.class::isInstance)
                .map(AiToolExecutionEvent.class::cast)
                .anyMatch(event -> "LEGACY_XINGCHEN_FILE_UPLOAD".equals(event.sceneCode())
                        && "xingchen-upload-file".equals(event.toolName())
                        && !event.success()
                        && event.errorMessage().contains("upstream down")));
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
