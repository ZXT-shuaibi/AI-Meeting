package com.hewei.hzyjy.xunzhi.career.harness.application;

import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunEventDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentRunEventMapper;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentRunMapper;
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

class AgentRunQueryServiceTest {

    @Test
    void returnsPagedSafeRunSummariesAndHonoursSceneAndStatusFilters() {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentRunEventMapper eventMapper = mock(AgentRunEventMapper.class);
        when(runMapper.selectList(any())).thenReturn(List.of(
                run("run-rag", "RAG_EXPERIMENT", "SUCCEEDED", 7L, 1_000L, 2_300L),
                run("run-report", "INTERVIEW_REPORT", "FAILED", 8L, 3_000L, 3_200L)
        ));
        AgentRunQueryService service = new AgentRunQueryService(runMapper, eventMapper);

        Map<String, Object> result = service.list(1, 20, "RAG_EXPERIMENT", "SUCCEEDED", null, null);

        assertEquals(1L, result.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(1, items.size());
        assertEquals("run-rag", items.getFirst().get("runId"));
        assertEquals(1_300L, items.getFirst().get("durationMs"));
        assertFalse(items.getFirst().containsKey("inputSummary"));
        assertFalse(items.getFirst().containsKey("metadataJson"));
    }

    @Test
    void returnsChronologicalTimelineWithOnlySafeEventMetadata() {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentRunEventMapper eventMapper = mock(AgentRunEventMapper.class);
        when(runMapper.selectList(any())).thenReturn(List.of(run("run-59", "RESUME_TAILOR", "SUCCEEDED", 7L, 1_000L, 2_500L)));
        when(eventMapper.selectList(any())).thenReturn(List.of(
                event("RUN_FINISHED", 2_400L, "{\"iterations\":2,\"jdText\":\"secret job description\"}"),
                event("RAG_RETRIEVE", 1_200L, "{\"resultCount\":3,\"resumeText\":\"secret resume\"}")
        ));
        AgentRunQueryService service = new AgentRunQueryService(runMapper, eventMapper);

        Map<String, Object> detail = service.detail("run-59");

        assertEquals("run-59", detail.get("runId"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) detail.get("events");
        assertEquals("RAG_RETRIEVE", events.getFirst().get("eventType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) events.getFirst().get("metrics");
        assertEquals(3, metrics.get("resultCount"));
        assertFalse(metrics.containsKey("resumeText"));
        assertTrue(events.get(1).get("message").toString().contains("RUN_FINISHED"));
    }

    @Test
    void exposesOnlyEnumShapedJdSafetySignalsInMonitoringTimeline() {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentRunEventMapper eventMapper = mock(AgentRunEventMapper.class);
        when(runMapper.selectList(any())).thenReturn(List.of(run("run-60", "JOB_MATCH", "SUCCEEDED", 7L, 1_000L, 2_500L)));
        when(eventMapper.selectList(any())).thenReturn(List.of(event("JD_SAFETY_CHECK", 1_200L,
                "{\"riskLevel\":\"HIGH\",\"riskSignals\":[\"INSTRUCTION_OVERRIDE\",\"secret JD sentence\"],\"normalizedInputDigest\":\"sha256:abc\"}")));
        AgentRunQueryService service = new AgentRunQueryService(runMapper, eventMapper);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) service.detail("run-60").get("events");
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) events.getFirst().get("metrics");

        assertEquals("HIGH", metrics.get("riskLevel"));
        assertEquals(List.of("INSTRUCTION_OVERRIDE"), metrics.get("riskSignals"));
        assertEquals("sha256:abc", metrics.get("normalizedInputDigest"));
    }

    @Test
    void returnsEmptyPageForExtremelyLargePageNumberInsteadOfOverflowingSubListIndex() {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentRunEventMapper eventMapper = mock(AgentRunEventMapper.class);
        when(runMapper.selectList(any())).thenReturn(List.of(run("run-61", "JOB_MATCH", "SUCCEEDED", 7L, 1_000L, 2_500L)));
        AgentRunQueryService service = new AgentRunQueryService(runMapper, eventMapper);

        Map<String, Object> result = service.list(Integer.MAX_VALUE, 100, null, null, null, null);

        assertEquals(1L, result.get("total"));
        assertEquals(List.of(), result.get("items"));
    }

    private AgentRunDO run(String runId, String sceneCode, String status, Long userId, long startedAt, long finishedAt) {
        AgentRunDO run = new AgentRunDO();
        run.setRunId(runId);
        run.setSceneCode(sceneCode);
        run.setStatus(status);
        run.setUserId(userId);
        run.setTraceId("trace-" + runId);
        run.setRagEnabled(true);
        run.setBusinessType("CAREER");
        run.setBusinessId("59");
        run.setStartedAt(new Date(startedAt));
        run.setFinishedAt(new Date(finishedAt));
        run.setInputSummary("JD 长度=180");
        return run;
    }

    private AgentRunEventDO event(String eventType, long occurredAt, String metadataJson) {
        AgentRunEventDO event = new AgentRunEventDO();
        event.setEventType(eventType);
        event.setStageCode(eventType);
        event.setStatus("RUNNING");
        event.setMessage(eventType + " 已完成");
        event.setOccurredAt(new Date(occurredAt));
        event.setMetadataJson(metadataJson);
        return event;
    }
}
