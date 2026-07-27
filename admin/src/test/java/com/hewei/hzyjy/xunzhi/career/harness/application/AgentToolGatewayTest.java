package com.hewei.hzyjy.xunzhi.career.harness.application;

import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentRunDO;
import com.hewei.hzyjy.xunzhi.career.harness.dao.mapper.AgentRunMapper;
import com.hewei.hzyjy.xunzhi.career.harness.model.AgentToolCode;
import com.hewei.hzyjy.xunzhi.career.harness.model.AgentToolCommand;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagExperimentDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagExperimentMapper;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolGatewayTest {
    @Test
    void returnsOnlySafeRagExperimentSummaryForResourceOwner() {
        RagExperimentMapper experimentMapper = mock(RagExperimentMapper.class);
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        RagExperimentDO experiment = new RagExperimentDO();
        experiment.setId(8L); experiment.setOwnerUserId(7L); experiment.setName("Java 检索基准");
        experiment.setStatus("COMPLETED"); experiment.setTopK(5); experiment.setConfigFingerprint("cfg-8");
        experiment.setJobDescription("这段 JD 原文绝不能被工具返回");
        when(experimentMapper.selectById(8L)).thenReturn(experiment);
        AgentToolGateway gateway = new AgentToolGateway(experimentMapper, runMapper, null);

        Map<String, Object> result = gateway.execute(new AgentToolCommand(AgentToolCode.READ_RAG_EXPERIMENT_SUMMARY, 7L, "8", null));

        assertEquals("Java 检索基准", result.get("name"));
        org.junit.jupiter.api.Assertions.assertFalse(result.containsKey("jobDescription"));
    }

    @Test
    void rejectsCrossUserResourceAndUnknownTool() {
        RagExperimentMapper experimentMapper = mock(RagExperimentMapper.class);
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        RagExperimentDO experiment = new RagExperimentDO(); experiment.setId(8L); experiment.setOwnerUserId(9L);
        when(experimentMapper.selectById(8L)).thenReturn(experiment);
        AgentToolGateway gateway = new AgentToolGateway(experimentMapper, runMapper, null);
        assertThrows(ClientException.class, () -> gateway.execute(new AgentToolCommand(AgentToolCode.READ_RAG_EXPERIMENT_SUMMARY, 7L, "8", null)));
        assertThrows(ClientException.class, () -> gateway.execute(new AgentToolCommand(null, 7L, "8", null)));
    }
}
