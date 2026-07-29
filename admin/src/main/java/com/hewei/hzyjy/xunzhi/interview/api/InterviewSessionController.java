package com.hewei.hzyjy.xunzhi.interview.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hewei.hzyjy.xunzhi.agent.api.io.resp.AgentMessageHistoryRespDTO;
import com.hewei.hzyjy.xunzhi.common.convention.annotation.CurrentUser;
import com.hewei.hzyjy.xunzhi.common.convention.context.UserContext;
import com.hewei.hzyjy.xunzhi.common.convention.result.Result;
import com.hewei.hzyjy.xunzhi.common.convention.result.Results;
import com.hewei.hzyjy.xunzhi.interview.api.io.req.InterviewAnswerReqDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.req.InterviewConversationPageReqDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.InterviewAnswerRespDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.InterviewConversationRespDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.InterviewQuestionRespDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.InterviewSessionCreateRespDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.InterviewSessionRestoreRespDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.RadarChartDTO;
import com.hewei.hzyjy.xunzhi.interview.flow.session.InterviewSessionFacade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/xunzhi/v1/interview")
@RequiredArgsConstructor
public class InterviewSessionController {

    private final InterviewSessionFacade interviewSessionFacade;

    @PostMapping("/sessions")
    public Result<InterviewSessionCreateRespDTO> createSession(@CurrentUser UserContext currentUser) {
        return Results.success(interviewSessionFacade.createSession(currentUser.getUserId()));
    }

    @GetMapping("/conversations")
    public Result<IPage<InterviewConversationRespDTO>> pageConversations(
            InterviewConversationPageReqDTO requestParam,
            @CurrentUser UserContext currentUser) {
        return Results.success(interviewSessionFacade.pageConversations(currentUser.getUserId(), requestParam));
    }

    @GetMapping("/conversations/{sessionId}/messages")
    public Result<List<AgentMessageHistoryRespDTO>> getConversationHistory(
            @PathVariable String sessionId,
            @CurrentUser UserContext currentUser) {
        return Results.success(interviewSessionFacade.getConversationHistory(sessionId, currentUser.getUserId()));
    }

    @GetMapping("/messages/history")
    public Result<IPage<AgentMessageHistoryRespDTO>> pageHistoryMessages(
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @CurrentUser UserContext currentUser) {
        return Results.success(
                interviewSessionFacade.pageHistoryMessages(sessionId, current, size, currentUser.getUserId()));
    }

    @PutMapping("/sessions/{sessionId}/finish")
    public Result<Void> finishSession(
            @PathVariable String sessionId,
            @CurrentUser UserContext currentUser) {
        interviewSessionFacade.finishSession(sessionId, currentUser.getUserId());
        return Results.success();
    }

    @PutMapping("/conversations/{sessionId}/end")
    public Result<Void> endConversation(
            @PathVariable String sessionId,
            @CurrentUser UserContext currentUser) {
        interviewSessionFacade.endConversation(sessionId, currentUser.getUserId());
        return Results.success();
    }

    @PostMapping("/sessions/{sessionId}/interview-questions")
    public Result<InterviewQuestionRespDTO> extractInterviewQuestions(
            @PathVariable String sessionId,
            @RequestParam("resumePdf") MultipartFile resumePdf,
            @RequestParam(value = "jobDescription", required = false) String jobDescription,
            @CurrentUser UserContext currentUser) {
        return Results.success(interviewSessionFacade.extractInterviewQuestions(
                sessionId, resumePdf, currentUser.getUserId(), currentUser.getUsername(), jobDescription));
    }

    @PostMapping("/sessions/{sessionId}/interview/answer")
    public Result<InterviewAnswerRespDTO> answerInterviewQuestion(
            @PathVariable String sessionId,
            @NotBlank(message = "题号不能为空")
            @Size(max = 32, message = "题号长度不能超过 32 个字符")
            @RequestParam("questionNumber") String questionNumber,
            @NotBlank(message = "回答内容不能为空")
            @Size(max = 5000, message = "回答内容长度不能超过 5000 个字符")
            @RequestParam("answerContent") String answerContent,
            @Size(max = 64, message = "请求幂等标识长度不能超过 64 个字符")
            @RequestParam(value = "requestId", required = false) String requestId,
            @CurrentUser UserContext currentUser) {
        InterviewAnswerReqDTO requestParam = new InterviewAnswerReqDTO();
        requestParam.setQuestionNumber(questionNumber);
        requestParam.setAnswerContent(answerContent);
        requestParam.setRequestId(requestId);
        return Results.success(
                interviewSessionFacade.answerInterviewQuestion(sessionId, requestParam, currentUser.getUserId()));
    }

    @PostMapping(value = "/sessions/{sessionId}/interview/answer-json", consumes = "application/json")
    public Result<InterviewAnswerRespDTO> answerInterviewQuestionJson(
            @PathVariable String sessionId,
            @Valid @RequestBody InterviewAnswerReqDTO requestParam,
            @CurrentUser UserContext currentUser) {
        return Results.success(
                interviewSessionFacade.answerInterviewQuestion(sessionId, requestParam, currentUser.getUserId()));
    }

    @GetMapping("/sessions/{sessionId}/next-question")
    public Result<InterviewAnswerRespDTO> getNextQuestion(
            @PathVariable String sessionId,
            @CurrentUser UserContext currentUser) {
        return Results.success(interviewSessionFacade.getNextQuestion(sessionId, currentUser.getUserId()));
    }

    @GetMapping("/sessions/{sessionId}/current-question")
    public Result<InterviewAnswerRespDTO> getCurrentQuestion(
            @PathVariable String sessionId,
            @CurrentUser UserContext currentUser) {
        return Results.success(interviewSessionFacade.getCurrentQuestion(sessionId, currentUser.getUserId()));
    }

    @GetMapping("/sessions/{sessionId}/restore")
    public Result<InterviewSessionRestoreRespDTO> restoreInterviewSession(
            @PathVariable String sessionId,
            @CurrentUser UserContext currentUser) {
        return Results.success(interviewSessionFacade.restoreInterviewSession(sessionId, currentUser.getUserId()));
    }

    @GetMapping("/sessions/{sessionId}/interview/questions")
    public Result<Map<String, String>> getSessionInterviewQuestions(
            @PathVariable String sessionId,
            @CurrentUser UserContext currentUser) {
        return Results.success(interviewSessionFacade.getSessionInterviewQuestions(sessionId, currentUser.getUserId()));
    }

    @GetMapping("/sessions/{sessionId}/interview/score")
    public Result<Integer> getSessionTotalScore(
            @PathVariable String sessionId,
            @CurrentUser UserContext currentUser) {
        return Results.success(interviewSessionFacade.getSessionTotalScore(sessionId, currentUser.getUserId()));
    }

    @GetMapping("/sessions/{sessionId}/interview/suggestions")
    public Result<Map<String, String>> getSessionInterviewSuggestions(
            @PathVariable String sessionId,
            @CurrentUser UserContext currentUser) {
        return Results.success(
                interviewSessionFacade.getSessionInterviewSuggestions(sessionId, currentUser.getUserId()));
    }

    @GetMapping("/sessions/{sessionId}/resume/score")
    public Result<Integer> getSessionResumeScore(
            @PathVariable String sessionId,
            @CurrentUser UserContext currentUser) {
        return Results.success(interviewSessionFacade.getSessionResumeScore(sessionId, currentUser.getUserId()));
    }

    @GetMapping("/sessions/{sessionId}/radar-chart")
    public Result<RadarChartDTO> getRadarChartData(
            @PathVariable String sessionId,
            @CurrentUser UserContext currentUser) {
        return Results.success(interviewSessionFacade.getRadarChartData(sessionId, currentUser.getUserId()));
    }

    @PostMapping("/sessions/{sessionId}/demeanor-evaluation")
    public Result<String> evaluateDemeanor(
            @PathVariable String sessionId,
            @RequestPart("userPhoto") MultipartFile userPhoto,
            @RequestParam(value = "sessionId", required = false) String requestSessionId,
            @CurrentUser UserContext currentUser) {
        return Results.success(interviewSessionFacade.evaluateDemeanor(
                sessionId,
                userPhoto,
                requestSessionId,
                currentUser.getUserId(),
                currentUser.getUsername()));
    }
}
