package com.hewei.hzyjy.xunzhi.career.observability;

import com.hewei.hzyjy.xunzhi.common.convention.result.Result;
import com.hewei.hzyjy.xunzhi.common.convention.result.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/xunzhi/v1/admin/ai-monitoring")
public class AiMonitoringController {

    private final AiMonitoringService monitoringService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestParam(defaultValue = "60") int minutes) {
        return Results.success(monitoringService.overview(minutes));
    }

    @GetMapping("/rag-stages")
    public Result<Map<String, Object>> ragStages(@RequestParam(defaultValue = "60") int minutes) {
        return Results.success(monitoringService.ragStages(minutes));
    }

    /**
     * 分页查看已结束的单次 AI 调用。接口只返回脱敏摘要，便于定位哪个业务服务触发了 RAG，
     * 不会返回简历、JD、提示词或工具原始输入输出。
     */
    @GetMapping("/invocations")
    public Result<Map<String, Object>> invocations(
            @RequestParam(defaultValue = "60") int minutes,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sceneCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) Boolean hasRag) {
        return Results.success(monitoringService.invocations(minutes, page, size, sceneCode, status, traceId, hasRag));
    }

    /** 返回某一次调用的 RAG 阶段时间线、耗时、降级状态及安全指标。 */
    @GetMapping("/invocations/{traceId}")
    public Result<Map<String, Object>> invocationDetail(@PathVariable String traceId) {
        return Results.success(monitoringService.invocationDetail(traceId));
    }
}
