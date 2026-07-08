package com.hewei.hzyjy.xunzhi.career.agent.interview;

import com.hewei.hzyjy.xunzhi.interview.application.runtime.InterviewSessionRuntimeSnapshotService;
import com.hewei.hzyjy.xunzhi.interview.dao.entity.InterviewSession;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionCacheService;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionService;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewSessionService;
import com.hewei.hzyjy.xunzhi.interview.service.model.InterviewFlowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class CareerInterviewExecutionBridge {

    private static final int MAX_PLANNED_QUESTIONS = 8;

    private final ObjectProvider<InterviewSessionService> interviewSessionServiceProvider;
    private final ObjectProvider<InterviewQuestionCacheService> interviewQuestionCacheServiceProvider;
    private final ObjectProvider<InterviewQuestionService> interviewQuestionServiceProvider;
    private final ObjectProvider<InterviewSessionRuntimeSnapshotService> runtimeSnapshotServiceProvider;

    public void publishPlan(Long userId, String sessionId, InterviewPlan plan) {
        if (userId == null || !StringUtils.hasText(sessionId) || plan == null) {
            throw new IllegalArgumentException("userId, sessionId and interview plan are required");
        }
        InterviewSessionService sessionService = requireBean(interviewSessionServiceProvider, "InterviewSessionService");
        InterviewQuestionCacheService cacheService = requireBean(interviewQuestionCacheServiceProvider, "InterviewQuestionCacheService");
        InterviewQuestionService questionService = requireBean(interviewQuestionServiceProvider, "InterviewQuestionService");

        InterviewSession session = sessionService.requireOwnedSession(sessionId, userId);
        if (session == null) {
            throw new IllegalStateException("Interview session not found or not owned by current user: " + sessionId);
        }
        List<String> questions = buildQuestions(plan);
        if (questions.isEmpty()) {
            throw new IllegalStateException("Interview plan produced no executable questions: " + sessionId);
        }
        List<String> suggestions = buildSuggestions(plan);
        String direction = buildDirection(plan);

        questionService.upsertStructuredExtraction(
                sessionId,
                null,
                session.getInterviewerAgentId(),
                session.getResumeFileUrl(),
                questions,
                suggestions,
                null,
                direction,
                buildResumeContext(userId, plan)
        );

        cacheService.cacheInterviewQuestions(sessionId, questions);
        cacheService.initInterviewFlow(sessionId, questions.size());
        if (!suggestions.isEmpty()) {
            cacheService.cacheInterviewSuggestions(sessionId, suggestions);
        }
        if (StringUtils.hasText(direction)) {
            cacheService.cacheInterviewDirection(sessionId, direction);
        }
        verifyExecutablePlanPublished(cacheService, sessionId, questions.size());
        sessionService.markReady(sessionId, userId, session.getResumeFileUrl(), direction);
        refreshRuntimeSnapshot(sessionId);
    }

    private <T> T requireBean(ObjectProvider<T> provider, String beanName) {
        T bean = provider.getIfAvailable();
        if (bean == null) {
            throw new IllegalStateException(beanName + " is required to publish career interview plan");
        }
        return bean;
    }

    private void verifyExecutablePlanPublished(InterviewQuestionCacheService cacheService, String sessionId, int expectedQuestionCount) {
        Map<String, String> cachedQuestions = cacheService.getSessionInterviewQuestions(sessionId);
        if (cachedQuestions == null || cachedQuestions.size() < expectedQuestionCount) {
            cacheService.loadInterviewQuestionsFromDatabase(sessionId);
            cachedQuestions = cacheService.getSessionInterviewQuestions(sessionId);
        }
        if (cachedQuestions == null || cachedQuestions.size() < expectedQuestionCount) {
            throw new IllegalStateException("Career interview plan was persisted but not readable from execution question cache: " + sessionId);
        }
        InterviewFlowState flowState = cacheService.getInterviewFlow(sessionId);
        if (flowState == null || flowState.getTotalQuestions() == null || flowState.getTotalQuestions() < expectedQuestionCount) {
            throw new IllegalStateException("Career interview flow was not initialized for executable plan: " + sessionId);
        }
    }

    private List<String> buildQuestions(InterviewPlan plan) {
        Set<String> questions = new LinkedHashSet<>();
        addQuestion(questions, plan.firstQuestion());
        if (plan.stages() != null) {
            for (InterviewStagePlan stage : plan.stages()) {
                if (stage == null || stage.questionSeeds() == null) {
                    continue;
                }
                for (String seed : stage.questionSeeds()) {
                    if (questions.size() >= MAX_PLANNED_QUESTIONS) {
                        return new ArrayList<>(questions);
                    }
                    if (StringUtils.hasText(seed)) {
                        addQuestion(questions, "Please go deeper on " + seed.trim()
                                + ": explain the scenario, your responsibility, tradeoffs, and measurable result.");
                    }
                }
            }
        }
        return new ArrayList<>(questions);
    }

    private void addQuestion(Set<String> questions, String question) {
        if (!StringUtils.hasText(question) || questions.size() >= MAX_PLANNED_QUESTIONS) {
            return;
        }
        questions.add(question.trim());
    }

    private List<String> buildSuggestions(InterviewPlan plan) {
        List<String> suggestions = new ArrayList<>();
        JdAlignmentResult alignment = plan.alignment();
        if (alignment != null) {
            if (StringUtils.hasText(alignment.summary())) {
                suggestions.add("JD alignment: " + alignment.summary().trim());
            }
            if (alignment.missingSkills() != null && !alignment.missingSkills().isEmpty()) {
                suggestions.add("Risk probes: " + String.join(", ", alignment.missingSkills()));
            }
        }
        if (plan.stages() != null) {
            for (InterviewStagePlan stage : plan.stages()) {
                if (stage != null && StringUtils.hasText(stage.goal())) {
                    String stageName = StringUtils.hasText(stage.stageName()) ? stage.stageName().trim() : "stage";
                    suggestions.add(stageName + ": " + stage.goal().trim());
                }
            }
        }
        return suggestions;
    }

    private String buildDirection(InterviewPlan plan) {
        if (plan.alignment() != null && StringUtils.hasText(plan.alignment().summary())) {
            return abbreviate(plan.alignment().summary().trim(), 120);
        }
        if (plan.stages() != null && !plan.stages().isEmpty()) {
            return plan.stages().stream()
                    .filter(stage -> stage != null && StringUtils.hasText(stage.stageName()))
                    .map(stage -> stage.stageName().trim())
                    .findFirst()
                    .orElse("career-interview-plan");
        }
        return "career-interview-plan";
    }

    private Map<String, Object> buildResumeContext(Long userId, InterviewPlan plan) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("source", "career-interview-plan");
        context.put("userId", userId);
        if (StringUtils.hasText(plan.sessionId())) {
            context.put("planSessionId", plan.sessionId());
        }
        if (StringUtils.hasText(plan.firstQuestion())) {
            context.put("firstQuestion", plan.firstQuestion());
        }
        if (plan.alignment() != null) {
            JdAlignmentResult alignment = plan.alignment();
            if (StringUtils.hasText(alignment.summary())) {
                context.put("alignmentSummary", alignment.summary());
            }
            if (alignment.missingSkills() != null && !alignment.missingSkills().isEmpty()) {
                context.put("missingSkills", alignment.missingSkills());
            }
        }
        context.put("stageCount", plan.stages() == null ? 0 : plan.stages().size());
        return context;
    }

    private void refreshRuntimeSnapshot(String sessionId) {
        InterviewSessionRuntimeSnapshotService snapshotService = runtimeSnapshotServiceProvider.getIfAvailable();
        if (snapshotService == null) {
            return;
        }
        try {
            snapshotService.refreshAfterQuestionExtraction(sessionId);
        } catch (Exception ex) {
            log.debug("Career interview plan cache published, but runtime snapshot refresh failed. sessionId={}", sessionId, ex);
        }
    }

    private String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
