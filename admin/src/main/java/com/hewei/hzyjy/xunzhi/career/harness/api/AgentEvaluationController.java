package com.hewei.hzyjy.xunzhi.career.harness.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.hewei.hzyjy.xunzhi.career.harness.api.io.CreateAgentEvaluationBaselineReqDTO;
import com.hewei.hzyjy.xunzhi.career.harness.application.AgentEvaluationService;
import com.hewei.hzyjy.xunzhi.career.harness.dao.entity.AgentEvaluationBaselineDO;
import com.hewei.hzyjy.xunzhi.common.convention.result.Result;
import com.hewei.hzyjy.xunzhi.common.convention.result.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 管理员统一查看 Agent 质量与 RAG 基线对比的只读/冻结入口。 */
@RestController
@RequiredArgsConstructor
@SaCheckRole("admin")
@RequestMapping("/api/xunzhi/v1/admin/agent-evaluations")
public class AgentEvaluationController {
    private final AgentEvaluationService agentEvaluationService;

    @GetMapping("/baselines")
    public Result<List<AgentEvaluationBaselineDO>> baselines() { return Results.success(agentEvaluationService.listBaselines()); }

    @PostMapping("/baselines")
    public Result<AgentEvaluationBaselineDO> createBaseline(@Valid @RequestBody CreateAgentEvaluationBaselineReqDTO request) {
        return Results.success(agentEvaluationService.createRagBaseline(request.getName(), request.getExperimentId()));
    }

    @GetMapping("/rag-comparison")
    public Result<Map<String, Object>> ragComparison(@RequestParam Long baselineId, @RequestParam Long experimentId) {
        return Results.success(agentEvaluationService.compareRagExperiment(baselineId, experimentId));
    }

    @GetMapping("/scene-quality")
    public Result<List<Map<String, Object>>> sceneQuality() { return Results.success(agentEvaluationService.sceneQuality()); }
}
