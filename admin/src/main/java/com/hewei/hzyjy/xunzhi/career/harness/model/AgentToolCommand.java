package com.hewei.hzyjy.xunzhi.career.harness.model;

/**
 * 内部工具调用命令。只接受资源编号，刻意不提供任意 SQL、路径、URL 或 shell 参数。
 * parentRunId 存在时会将工具调用写入该 Agent 运行时间线。
 */
public record AgentToolCommand(AgentToolCode toolCode, Long callerUserId, String resourceId, String parentRunId) {
}
