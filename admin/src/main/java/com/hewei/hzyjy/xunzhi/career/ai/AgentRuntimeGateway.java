package com.hewei.hzyjy.xunzhi.career.ai;

import java.util.Map;

public interface AgentRuntimeGateway {

    <T> T invoke(String agentName, String methodName, Map<String, Object> variables, Class<T> responseType);
}
