package com.hewei.hzyjy.xunzhi.career.harness.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.hewei.hzyjy.xunzhi.career.harness.application.AgentNotificationOutboxService;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentNotificationOutboxDO;
import com.hewei.hzyjy.xunzhi.career.harness.model.AgentToolCode;
import com.hewei.hzyjy.xunzhi.common.convention.result.Result;
import com.hewei.hzyjy.xunzhi.common.convention.result.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

/** 管理端工具治理视图：公开白名单与通知状态，而不暴露外部配置、密钥或工具原始参数。 */
@RestController @RequiredArgsConstructor @SaCheckRole("admin")
@RequestMapping("/api/xunzhi/v1/admin/agent-tools")
public class AgentToolGovernanceController {
    private final AgentNotificationOutboxService notificationOutboxService;
    @GetMapping public Result<List<Map<String, Object>>> tools() {
        return Results.success(List.of(
                Map.of("code", AgentToolCode.READ_RAG_EXPERIMENT_SUMMARY.name(), "mode", "READ_ONLY", "description", "读取本人已完成 RAG 实验的脱敏指标摘要"),
                Map.of("code", AgentToolCode.READ_AGENT_RUN_SUMMARY.name(), "mode", "READ_ONLY", "description", "读取本人 Agent 运行的脱敏状态与耗时摘要"),
                Map.of("code", "career.notify_completion", "mode", "MCP_NOTIFICATION", "description", "任务成功后通过 Outbox 请求已配置的 MCP 通知桥；默认禁用")));
    }
    @GetMapping("/notifications") public Result<List<AgentNotificationOutboxDO>> notifications(@RequestParam(defaultValue = "30") int limit) {
        return Results.success(notificationOutboxService.listRecent(limit));
    }
}
