package com.hewei.hzyjy.xunzhi.career.harness.application;
import java.util.Map;
/** MCP 通知适配器契约；可替换为邮件、企业微信或正式 MCP Server，不影响 Outbox 语义。 */
public interface McpNotificationClient { McpNotificationResult send(Map<String, Object> payload); }
