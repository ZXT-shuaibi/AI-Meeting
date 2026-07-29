package com.hewei.hzyjy.xunzhi.career.harness.application;
import org.springframework.stereotype.Service;
import java.util.Map;
/** 默认适配器：未明确配置外部 MCP 通知桥时，禁止出网并返回可解释的未启用状态。 */
@Service public class DisabledMcpNotificationClient implements McpNotificationClient {
    @Override public McpNotificationResult send(Map<String, Object> payload) { return new McpNotificationResult(false, false, "MCP 通知桥未启用"); }
}
