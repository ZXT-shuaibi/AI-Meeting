package com.hewei.hzyjy.xunzhi.career.harness.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.hewei.hzyjy.xunzhi.career.harness.application.AgentRunQueryService;
import com.hewei.hzyjy.xunzhi.common.convention.result.Result;
import com.hewei.hzyjy.xunzhi.common.convention.result.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.Map;

/**
 * 管理员查看 Agent 业务运行总账与阶段时间线的入口。
 *
 * <p>此接口仅用于运营、排障和实验复盘；角色由服务端 Sa-Token 校验，绝不信任前端传入的管理员标记。</p>
 */
@RestController
@RequiredArgsConstructor
@SaCheckRole("admin")
@RequestMapping("/api/xunzhi/v1/admin/agent-runs")
public class AgentRunMonitoringController {

    private final AgentRunQueryService agentRunQueryService;

    @GetMapping
    public Result<Map<String, Object>> runs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sceneCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long fromTime,
            @RequestParam(required = false) Long toTime) {
        return Results.success(agentRunQueryService.list(
                page, size, sceneCode, status, toDate(fromTime), toDate(toTime)));
    }

    @GetMapping("/{runId}")
    public Result<Map<String, Object>> detail(@PathVariable String runId) {
        return Results.success(agentRunQueryService.detail(runId));
    }

    private Date toDate(Long epochMillis) {
        return epochMillis == null || epochMillis < 0 ? null : new Date(epochMillis);
    }
}
