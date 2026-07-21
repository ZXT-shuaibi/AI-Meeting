package com.hewei.hzyjy.xunzhi.career.api;

import com.hewei.hzyjy.xunzhi.career.agent.cv.CvOptimizationResult;
import com.hewei.hzyjy.xunzhi.career.agent.cv.CvReview;
import com.hewei.hzyjy.xunzhi.career.agent.interview.InterviewPlan;
import com.hewei.hzyjy.xunzhi.career.agent.interview.ReflectionResult;
import com.hewei.hzyjy.xunzhi.career.api.io.InterviewPlanReqDTO;
import com.hewei.hzyjy.xunzhi.career.api.io.InterviewReflectReqDTO;
import com.hewei.hzyjy.xunzhi.career.api.io.JobMatchReqDTO;
import com.hewei.hzyjy.xunzhi.career.api.io.ResumeOptimizeReqDTO;
import com.hewei.hzyjy.xunzhi.career.api.io.ResumeMatchOrderReqDTO;
import com.hewei.hzyjy.xunzhi.career.resume.application.JobMatchTaskResult;
import com.hewei.hzyjy.xunzhi.career.resume.application.JobMatchHistoryItem;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeApplicationService;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeEmbeddingResult;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeOptimizationHistoryResult;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeParseTaskResult;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeRenderStorageResult;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeUploadResult;
import com.hewei.hzyjy.xunzhi.career.resume.render.ResumeRenderArtifact;
import com.hewei.hzyjy.xunzhi.common.convention.annotation.CurrentUser;
import com.hewei.hzyjy.xunzhi.common.convention.context.UserContext;
import com.hewei.hzyjy.xunzhi.common.convention.result.Result;
import com.hewei.hzyjy.xunzhi.common.convention.result.Results;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/xunzhi/v1")
public class ResumeCareerController {

    private static final long RESUME_OPTIMIZATION_STREAM_TIMEOUT_MILLIS = 15 * 60 * 1000L;
    private static final Logger log = LoggerFactory.getLogger(ResumeCareerController.class);

    private final ResumeApplicationService resumeApplicationService;
    private final TaskExecutor careerTaskExecutor;

    public ResumeCareerController(
            ResumeApplicationService resumeApplicationService,
            @Qualifier("careerTaskExecutor") TaskExecutor careerTaskExecutor) {
        this.resumeApplicationService = resumeApplicationService;
        this.careerTaskExecutor = careerTaskExecutor;
    }

    @PostMapping(value = "/resumes/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ResumeUploadResult> upload(
            @RequestPart("resume") MultipartFile resume,
            @CurrentUser UserContext currentUser) {
        return Results.success(resumeApplicationService.upload(currentUser.getUserId(), resume));
    }

    @PostMapping(value = "/resumes/upload-async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ResumeParseTaskResult> uploadAsync(
            @RequestPart("resume") MultipartFile resume,
            @RequestPart(value = "cvType", required = false) String cvType,
            @CurrentUser UserContext currentUser) {
        return Results.success(resumeApplicationService.uploadAsync(currentUser.getUserId(), resume, cvType));
    }

    @GetMapping("/resumes/parse-tasks/{taskId}")
    public Result<ResumeParseTaskResult> getParseTask(
            @PathVariable String taskId,
            @CurrentUser UserContext currentUser) {
        return Results.success(resumeApplicationService.getParseTask(currentUser.getUserId(), taskId));
    }

    @GetMapping("/resumes/parse-tasks")
    public Result<java.util.List<ResumeParseTaskResult>> listParseTasks(
            @RequestParam(value = "status", required = false) String status,
            @CurrentUser UserContext currentUser) {
        return Results.success(resumeApplicationService.listParseTasks(currentUser.getUserId(), status));
    }

    @PutMapping("/resumes/match-order")
    public Result<Void> updateMatchResumeOrder(
            @Valid @RequestBody ResumeMatchOrderReqDTO requestParam,
            @CurrentUser UserContext currentUser) {
        resumeApplicationService.updateMatchResumeOrder(currentUser.getUserId(), requestParam.getResumeIds());
        return Results.success();
    }

    @GetMapping("/resumes/optimization-history")
    public Result<java.util.List<ResumeOptimizationHistoryResult>> listOptimizationHistory(
            @CurrentUser UserContext currentUser) {
        return Results.success(resumeApplicationService.listOptimizationHistory(currentUser.getUserId()));
    }

    @PostMapping("/resumes/parse-tasks/{taskId}/cancel")
    public Result<ResumeParseTaskResult> cancelParseTask(
            @PathVariable String taskId,
            @CurrentUser UserContext currentUser) {
        return Results.success(resumeApplicationService.cancelParseTask(currentUser.getUserId(), taskId));
    }

    @PostMapping("/resumes/parse-tasks/{taskId}/retry")
    public Result<ResumeParseTaskResult> retryParseTask(
            @PathVariable String taskId,
            @CurrentUser UserContext currentUser) {
        return Results.success(resumeApplicationService.retryParseTask(currentUser.getUserId(), taskId));
    }

    @PostMapping("/resumes/{resumeId}/embedding")
    public Result<ResumeEmbeddingResult> embedding(
            @PathVariable Long resumeId,
            @CurrentUser UserContext currentUser) {
        return Results.success(resumeApplicationService.embedding(currentUser.getUserId(), resumeId));
    }

    @GetMapping("/resumes/{resumeId}/render/{format}")
    public ResponseEntity<byte[]> renderResume(
            @PathVariable Long resumeId,
            @PathVariable String format,
            @CurrentUser UserContext currentUser) {
        ResumeRenderArtifact artifact = resumeApplicationService.renderResume(currentUser.getUserId(), resumeId, format);
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(artifact.contentType());
        } catch (Exception ex) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(artifact.bytes().length)
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(artifact.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(artifact.bytes());
    }

    @PostMapping("/resumes/{resumeId}/render/{format}/store")
    public Result<ResumeRenderStorageResult> renderResumeAndStore(
            @PathVariable Long resumeId,
            @PathVariable String format,
            @CurrentUser UserContext currentUser) {
        return Results.success(resumeApplicationService.renderResumeAndStore(currentUser.getUserId(), resumeId, format));
    }

    @PostMapping("/jobs/match-resumes")
    public Result<JobMatchTaskResult> matchResumes(
            @Valid @RequestBody JobMatchReqDTO requestParam,
            @CurrentUser UserContext currentUser) {
        return Results.success(resumeApplicationService.matchResumes(
                currentUser.getUserId(),
                requestParam.getJobDescription(),
                requestParam.getResumeIds(),
                requestParam.getLimit() == null ? 3 : requestParam.getLimit(),
                requestParam.getRagEnabled()
        ));
    }

    @GetMapping("/jobs/match-history")
    public Result<com.hewei.hzyjy.xunzhi.career.resume.application.JobMatchHistoryPage> listMatchHistory(
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @CurrentUser UserContext currentUser) {
        return Results.success(resumeApplicationService.listMatchHistory(currentUser.getUserId(), limit));
    }

    @GetMapping("/jobs/match-tasks/{taskId}")
    public Result<JobMatchTaskResult> getMatchTask(
            @PathVariable String taskId,
            @CurrentUser UserContext currentUser) {
        return Results.success(resumeApplicationService.getMatchTask(currentUser.getUserId(), taskId));
    }

    @PostMapping("/resumes/{resumeId}/optimize")
    public Result<CvOptimizationResult> optimize(
            @PathVariable Long resumeId,
            @Valid @RequestBody ResumeOptimizeReqDTO requestParam,
            @CurrentUser UserContext currentUser) {
        return Results.success(resumeApplicationService.optimizeWithRagOverride(currentUser.getUserId(), resumeId, requestParam.getJobDescription(), requestParam.getRagEnabled()));
    }

    @PostMapping(value = "/resumes/{resumeId}/optimize/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter optimizeStream(
            @PathVariable Long resumeId,
            @Valid @RequestBody ResumeOptimizeReqDTO requestParam,
            @CurrentUser UserContext currentUser) throws IOException {
        // 一次优化可能串行调用多个模型或工具，因此流式响应时长必须覆盖单次模型调用的默认超时。
        SseEmitter emitter = new SseEmitter(RESUME_OPTIMIZATION_STREAM_TIMEOUT_MILLIS);
        emitter.onTimeout(() -> log.warn("简历优化流式响应超时，用户编号={}，简历编号={}",
                currentUser.getUserId(), resumeId));
        emitter.onError(ex -> log.warn("简历优化流式响应传输失败，用户编号={}，简历编号={}",
                currentUser.getUserId(), resumeId, ex));
        sendEvent(emitter, "START", "简历优化已开始");
        careerTaskExecutor.execute(() -> {
            try {
                CvOptimizationResult result = resumeApplicationService.optimizeWithRagOverride(
                        currentUser.getUserId(),
                        resumeId,
                        requestParam.getJobDescription(),
                        requestParam.getRagEnabled(),
                        review -> {
                            try {
                                sendEvent(emitter, "ITERATION", iterationEventData(review));
                            } catch (IOException ex) {
                                throw new IllegalStateException("Failed to stream optimization iteration", ex);
                            }
                        });
                log.info("简历优化已完成，正在发送流式结果，用户编号={}，简历编号={}",
                        currentUser.getUserId(), resumeId);
                sendEvent(emitter, "COMPLETE", result);
            } catch (Exception ex) {
                log.warn("简历优化失败，正在发送流式错误结果，用户编号={}，简历编号={}",
                        currentUser.getUserId(), resumeId, ex);
                try {
                    sendEvent(emitter, "ERROR", ex.getMessage() == null ? "简历优化失败" : ex.getMessage());
                } catch (Exception sendError) {
                log.warn("简历优化错误事件写入失败，用户编号={}，简历编号={}",
                            currentUser.getUserId(), resumeId, sendError);
                }
            } finally {
        // 客户端断连会导致事件写入失败；无论成功或失败都必须释放 SSE 响应资源。
                emitter.complete();
            }
        });
        return emitter;
    }

    protected void sendEvent(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }

    private Map<String, Object> iterationEventData(CvReview review) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("score", review == null ? null : review.score());
        payload.put("feedback", review == null ? null : review.feedback());
        payload.put("status", "PROCESSING");
        return payload;
    }

    @PostMapping("/interview/plans")
    public Result<InterviewPlan> planInterview(
            @Valid @RequestBody InterviewPlanReqDTO requestParam,
            @CurrentUser UserContext currentUser) {
        return Results.success(resumeApplicationService.planInterview(
                currentUser.getUserId(),
                requestParam.getSessionId(),
                requestParam.getResumeId(),
                requestParam.getJobDescription()
        ));
    }

    @PostMapping("/interview/reflections")
    public Result<ReflectionResult> reflect(
            @Valid @RequestBody InterviewReflectReqDTO requestParam,
            @CurrentUser UserContext currentUser) {
        return Results.success(resumeApplicationService.reflect(
                currentUser.getUserId(),
                requestParam.getSessionId(),
                requestParam.getResumeId(),
                requestParam.getCurrentQuestion(),
                requestParam.getUserAnswer()
        ));
    }
}
