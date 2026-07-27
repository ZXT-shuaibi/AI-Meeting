package com.hewei.hzyjy.xunzhi.career.observability;

import com.hewei.hzyjy.xunzhi.career.observability.dao.entity.AiInvocationTraceDO;
import com.hewei.hzyjy.xunzhi.career.observability.dao.entity.AiToolExecutionDO;
import com.hewei.hzyjy.xunzhi.career.observability.dao.mapper.AiInvocationTraceMapper;
import com.hewei.hzyjy.xunzhi.career.observability.dao.mapper.AiToolExecutionMapper;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiMonitoringServiceTest {

    @Test
    void invocationDetailShouldAggregateRagStagesAndExcludeSensitivePayloads() {
        AiInvocationTraceMapper traceMapper = mock(AiInvocationTraceMapper.class);
        AiToolExecutionMapper toolMapper = mock(AiToolExecutionMapper.class);
        AiInvocationTraceDO trace = completedTrace();
        when(traceMapper.selectList(any())).thenReturn(List.of(trace));
        when(toolMapper.selectList(any())).thenReturn(List.of(
                ragStage("RAG_STAGE_VECTOR_RETRIEVAL", true, 12L, null, "{\"inputCount\":8,\"outputCount\":5}"),
                ragStage("RAG_STAGE_RERANK", false, 20L, "模型超时，已本地降级", "{\"fallbackReason\":\"timeout\",\"cacheHit\":false}")
        ));

        AiMonitoringService service = new AiMonitoringService(traceMapper, toolMapper);

        Map<String, Object> detail = service.invocationDetail("trace-001");

        assertEquals("trace-001", detail.get("traceId"));
        assertEquals(32L, detail.get("ragDurationMs"));
        assertEquals(1L, detail.get("ragFallbackCount"));
        assertFalse(detail.containsKey("inputSummary"));
        assertFalse(detail.containsKey("outputSummary"));
        assertFalse(detail.containsKey("errorStackTrace"));
        List<Map<String, Object>> stages = castRows(detail.get("stages"));
        assertEquals(2, stages.size());
        assertEquals("向量召回", stages.get(0).get("displayName"));
        assertEquals("Rerank 重排", stages.get(1).get("displayName"));
        assertFalse(stages.get(0).containsKey("toolInput"));
        assertFalse(stages.get(0).containsKey("toolOutput"));
        assertEquals(8, ((Map<?, ?>) stages.get(0).get("metrics")).get("inputCount"));
        assertEquals("timeout", ((Map<?, ?>) stages.get(1).get("metrics")).get("fallbackReason"));
    }

    @Test
    void invocationPageShouldExposeRagCoverageAndPerCallFallbackSummary() {
        AiInvocationTraceMapper traceMapper = mock(AiInvocationTraceMapper.class);
        AiToolExecutionMapper toolMapper = mock(AiToolExecutionMapper.class);
        when(traceMapper.selectList(any())).thenReturn(List.of(completedTrace()));
        when(toolMapper.selectList(any())).thenReturn(List.of(
                ragStage("RAG_STAGE_VECTOR_RETRIEVAL", true, 12L, null, "{}"),
                ragStage("RAG_STAGE_RERANK", false, 20L, "模型超时，已本地降级", "{}")
        ));
        AiMonitoringService service = new AiMonitoringService(traceMapper, toolMapper);

        Map<String, Object> page = service.invocations(60, 1, 20, null, null, null, null);

        assertEquals(1L, page.get("total"));
        assertEquals(1L, page.get("ragTraceCount"));
        assertEquals(100D, page.get("ragCoverageRate"));
        Map<String, Object> row = castRows(page.get("items")).getFirst();
        assertTrue((Boolean) row.get("hasRag"));
        assertEquals(32L, row.get("ragDurationMs"));
        assertEquals(1L, row.get("ragFallbackCount"));
    }

    @Test
    void overviewShouldUseOnlyRagRequestedBusinessCallsAsCoverageDenominator() {
        AiInvocationTraceMapper traceMapper = mock(AiInvocationTraceMapper.class);
        AiToolExecutionMapper toolMapper = mock(AiToolExecutionMapper.class);
        AiInvocationTraceDO ragBusinessTrace = completedTrace();
        ragBusinessTrace.setMetadataJson("{\"ragRequested\":true,\"ragEnabled\":true}");
        AiInvocationTraceDO ttsTrace = completedTrace();
        ttsTrace.setTraceId("tts-001");
        ttsTrace.setSceneCode("TTS_QUERY");
        ttsTrace.setProvider("xunfei");
        ttsTrace.setModelOrFlowId("long-text-tts");
        when(traceMapper.selectList(any())).thenReturn(List.of(ragBusinessTrace, ttsTrace));
        when(toolMapper.selectList(any())).thenReturn(List.of(
                ragStage("RAG_STAGE_VECTOR_RETRIEVAL", true, 12L, null, "{}")
        ));
        AiMonitoringService service = new AiMonitoringService(traceMapper, toolMapper);

        Map<String, Object> overview = service.overview(60);
        Map<String, Object> coverage = castMap(overview.get("ragCoverage"));

        assertEquals(1L, coverage.get("businessEligibleCount"));
        assertEquals(100D, coverage.get("businessCoverageRate"));
        assertEquals(50D, coverage.get("globalParticipationRate"));
    }

    private static AiInvocationTraceDO completedTrace() {
        AiInvocationTraceDO trace = new AiInvocationTraceDO();
        trace.setTraceId("trace-001");
        trace.setSceneCode("resume-job-match");
        trace.setAgentName("岗位匹配服务");
        trace.setProvider("dashscope");
        trace.setModelOrFlowId("text-embedding-v4");
        trace.setEventType("END");
        trace.setDurationMs(88L);
        trace.setSessionId("session-001");
        trace.setCreateTime(new Date());
        return trace;
    }

    private static AiToolExecutionDO ragStage(String name, boolean success, long duration, String error, String metadata) {
        AiToolExecutionDO stage = new AiToolExecutionDO();
        stage.setTraceId("trace-001");
        stage.setToolName(name);
        stage.setSuccess(success);
        stage.setExecutionTimeMs(duration);
        stage.setErrorMessage(error);
        stage.setMetadataJson(metadata);
        stage.setInvocationOrder(name.endsWith("RERANK") ? 2 : 1);
        stage.setEventTime(new Date());
        return stage;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castRows(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
