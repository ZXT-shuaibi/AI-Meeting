package com.hewei.hzyjy.xunzhi.career.api;

import com.hewei.hzyjy.xunzhi.career.agent.cv.CvOptimizationResult;
import com.hewei.hzyjy.xunzhi.career.agent.interview.InterviewPlan;
import com.hewei.hzyjy.xunzhi.career.agent.interview.ReflectionResult;
import com.hewei.hzyjy.xunzhi.career.api.io.InterviewPlanReqDTO;
import com.hewei.hzyjy.xunzhi.career.api.io.InterviewReflectReqDTO;
import com.hewei.hzyjy.xunzhi.career.api.io.JobMatchReqDTO;
import com.hewei.hzyjy.xunzhi.career.api.io.ResumeOptimizeReqDTO;
import com.hewei.hzyjy.xunzhi.career.resume.application.JobMatchTaskResult;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeApplicationService;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeEmbeddingResult;
import com.hewei.hzyjy.xunzhi.career.resume.application.ResumeUploadResult;
import com.hewei.hzyjy.xunzhi.common.convention.annotation.CurrentUser;
import com.hewei.hzyjy.xunzhi.common.convention.context.UserContext;
import com.hewei.hzyjy.xunzhi.common.convention.result.Result;
import com.hewei.hzyjy.xunzhi.common.convention.result.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Validated
@RestController
@RequestMapping("/api/xunzhi/v1")
@RequiredArgsConstructor
public class ResumeCareerController {

    private final ResumeApplicationService resumeApplicationService;

    @PostMapping(value = "/resumes/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ResumeUploadResult> upload(
            @RequestPart("resume") MultipartFile resume,
            @CurrentUser UserContext currentUser) {
        return Results.success(resumeApplicationService.upload(currentUser.getUserId(), resume));
    }

    @PostMapping("/resumes/{resumeId}/embedding")
    public Result<ResumeEmbeddingResult> embedding(@PathVariable Long resumeId) {
        return Results.success(resumeApplicationService.embedding(resumeId));
    }

    @PostMapping("/jobs/match-resumes")
    public Result<JobMatchTaskResult> matchResumes(@Valid @RequestBody JobMatchReqDTO requestParam) {
        return Results.success(resumeApplicationService.matchResumes(
                requestParam.getJobDescription(),
                requestParam.getLimit() == null ? 3 : requestParam.getLimit()
        ));
    }

    @GetMapping("/jobs/match-tasks/{taskId}")
    public Result<JobMatchTaskResult> getMatchTask(@PathVariable String taskId) {
        return Results.success(resumeApplicationService.getMatchTask(taskId));
    }

    @PostMapping("/resumes/{resumeId}/optimize")
    public Result<CvOptimizationResult> optimize(
            @PathVariable Long resumeId,
            @Valid @RequestBody ResumeOptimizeReqDTO requestParam) {
        return Results.success(resumeApplicationService.optimize(resumeId, requestParam.getJobDescription()));
    }

    @PostMapping(value = "/resumes/{resumeId}/optimize/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter optimizeStream(
            @PathVariable Long resumeId,
            @Valid @RequestBody ResumeOptimizeReqDTO requestParam) throws IOException {
        SseEmitter emitter = new SseEmitter(120000L);
        emitter.send(SseEmitter.event().name("START").data("resume optimization started"));
        CvOptimizationResult result = resumeApplicationService.optimize(resumeId, requestParam.getJobDescription());
        emitter.send(SseEmitter.event().name("COMPLETE").data(result));
        emitter.complete();
        return emitter;
    }

    @PostMapping("/interview/plans")
    public Result<InterviewPlan> planInterview(@Valid @RequestBody InterviewPlanReqDTO requestParam) {
        return Results.success(resumeApplicationService.planInterview(
                requestParam.getSessionId(),
                requestParam.getResumeId(),
                requestParam.getJobDescription()
        ));
    }

    @PostMapping("/interview/reflections")
    public Result<ReflectionResult> reflect(@Valid @RequestBody InterviewReflectReqDTO requestParam) {
        return Results.success(resumeApplicationService.reflect(
                requestParam.getSessionId(),
                requestParam.getResumeId(),
                requestParam.getCurrentQuestion(),
                requestParam.getUserAnswer()
        ));
    }
}
