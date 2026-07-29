package com.hewei.hzyjy.xunzhi.career.harness.application;
/** 外部桥接调用的安全结果，禁止返回或记录外部服务响应原文。 */
public record McpNotificationResult(boolean delivered, boolean enabled, String errorSummary) { }
