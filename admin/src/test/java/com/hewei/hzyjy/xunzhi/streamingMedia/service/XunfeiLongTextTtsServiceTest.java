package com.hewei.hzyjy.xunzhi.streamingMedia.service;

import com.alibaba.fastjson2.JSONObject;
import com.hewei.hzyjy.xunzhi.common.config.xunfei.XunfeiLatProperties;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import com.hewei.hzyjy.xunzhi.career.observability.AiInvocationFailedEvent;
import com.hewei.hzyjy.xunzhi.career.observability.AiInvocationStartedEvent;
import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import com.hewei.hzyjy.xunzhi.media.api.io.req.LongTextTtsReqDTO;
import com.hewei.hzyjy.xunzhi.media.api.io.resp.LongTextTtsTaskRespDTO;
import com.hewei.hzyjy.xunzhi.media.infrastructure.integration.XunfeiLongTextTtsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XunfeiLongTextTtsServiceTest {

    private XunfeiLongTextTtsService service;

    @BeforeEach
    void setUp() {
        service = new XunfeiLongTextTtsService(new XunfeiLatProperties());
    }

    @Test
    void validateCreateRequest_ShouldAllowTextAtOfficialUpperBound() {
        LongTextTtsReqDTO request = new LongTextTtsReqDTO();
        request.setText("a".repeat(100000));

        assertDoesNotThrow(() -> invokeVoid("validateCreateRequest", request));
    }

    @Test
    void validateCreateRequest_ShouldRejectTextAboveOfficialUpperBound() {
        LongTextTtsReqDTO request = new LongTextTtsReqDTO();
        request.setText("a".repeat(100001));

        assertThrows(ClientException.class, () -> invokeVoid("validateCreateRequest", request));
    }

    @Test
    void validateCreateRequest_ShouldRejectOutOfRangeSpeed() {
        LongTextTtsReqDTO request = new LongTextTtsReqDTO();
        request.setText("hello");
        request.setSpeed(101);

        assertThrows(ClientException.class, () -> invokeVoid("validateCreateRequest", request));
    }

    @Test
    void validateCreateRequest_ShouldRejectInvalidRhyValue() {
        LongTextTtsReqDTO request = new LongTextTtsReqDTO();
        request.setText("hello");
        request.setRhy(2);

        assertThrows(ClientException.class, () -> invokeVoid("validateCreateRequest", request));
    }

    @Test
    void buildCreateTaskBody_ShouldIncludePybufConfig_AndNotSendStreamingFields() throws Exception {
        LongTextTtsReqDTO request = new LongTextTtsReqDTO();
        request.setText("hello world");

        JSONObject body = invoke("buildCreateTaskBody", LongTextTtsReqDTO.class, request);

        JSONObject dts = body.getJSONObject("parameter").getJSONObject("dts");
        assertTrue(dts.containsKey("pybuf"));
        assertEquals("utf8", dts.getJSONObject("pybuf").getString("encoding"));

        JSONObject textNode = body.getJSONObject("payload").getJSONObject("text");
        assertFalse(textNode.containsKey("status"));
        assertFalse(textNode.containsKey("seq"));
    }

    @Test
    void parseTaskResponse_ShouldReadPybufUrlFromPayloadPybufText() throws Exception {
        JSONObject response = new JSONObject();

        JSONObject header = new JSONObject();
        header.put("code", 0);
        header.put("message", "success");
        header.put("task_id", "task-1");
        header.put("task_status", 5);
        response.put("header", header);

        JSONObject payload = new JSONObject();
        JSONObject audio = new JSONObject();
        audio.put("audio", Base64.getEncoder().encodeToString("https://audio.example".getBytes(StandardCharsets.UTF_8)));
        payload.put("audio", audio);

        JSONObject pybuf = new JSONObject();
        pybuf.put("text", Base64.getEncoder().encodeToString("https://pybuf.example".getBytes(StandardCharsets.UTF_8)));
        payload.put("pybuf", pybuf);
        response.put("payload", payload);

        LongTextTtsTaskRespDTO result = invoke("parseTaskResponse", JSONObject.class, response);

        assertEquals("https://audio.example", result.getAudioUrl());
        assertEquals("https://pybuf.example", result.getPybufUrl());
        assertTrue(result.getSuccess());
        assertTrue(result.getCompleted());
    }

    @Test
    void createTask_ShouldPublishUnifiedTraceWhenCredentialsMissing() {
        RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
        XunfeiLongTextTtsService tracedService = new XunfeiLongTextTtsService(
                new XunfeiLatProperties(),
                traceProvider(new AiTracePublisher(eventPublisher))
        );
        LongTextTtsReqDTO request = new LongTextTtsReqDTO();
        request.setText("hello");

        assertThrows(Exception.class, () -> tracedService.createTask(request));

        assertTrue(eventPublisher.events().stream()
                .filter(AiInvocationStartedEvent.class::isInstance)
                .map(AiInvocationStartedEvent.class::cast)
                .anyMatch(event -> "LEGACY_XUNFEI_TTS_CREATE".equals(event.sceneCode())));
        assertTrue(eventPublisher.events().stream()
                .filter(AiInvocationFailedEvent.class::isInstance)
                .map(AiInvocationFailedEvent.class::cast)
                .anyMatch(event -> "LEGACY_XUNFEI_TTS_CREATE".equals(event.sceneCode())
                        && event.errorMessage().contains("credentials")));
    }

    private void invokeVoid(String methodName, Object arg) throws Exception {
        Method method = XunfeiLongTextTtsService.class.getDeclaredMethod(methodName, arg.getClass());
        method.setAccessible(true);
        try {
            method.invoke(service, arg);
        } catch (InvocationTargetException ex) {
            Throwable target = ex.getTargetException();
            if (target instanceof Exception exception) {
                throw exception;
            }
            throw ex;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(String methodName, Class<?> paramType, Object arg) throws Exception {
        Method method = XunfeiLongTextTtsService.class.getDeclaredMethod(methodName, paramType);
        method.setAccessible(true);
        try {
            return (T) method.invoke(service, arg);
        } catch (InvocationTargetException ex) {
            Throwable target = ex.getTargetException();
            if (target instanceof Exception exception) {
                throw exception;
            }
            throw ex;
        }
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
