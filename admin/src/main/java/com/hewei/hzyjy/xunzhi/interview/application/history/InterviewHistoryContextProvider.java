package com.hewei.hzyjy.xunzhi.interview.application.history;

import com.hewei.hzyjy.xunzhi.interview.application.runtime.InterviewSessionRuntimeSnapshotService;
import com.hewei.hzyjy.xunzhi.interview.dao.entity.InterviewSessionRuntimeSnapshot;
import com.hewei.hzyjy.xunzhi.interview.service.model.InterviewTurnLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 将既有运行时快照和轮次归档投影为安全、长度受限的 Agent 上下文。
 * 该组件只读，不写入派生数据，也不替代 Redis/Mongo 中原有的会话事实源。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewHistoryContextProvider {

    private static final int RECENT_TURN_LIMIT = 6;
    private static final int LOW_SCORE_LIMIT = 3;
    private static final int LOW_SCORE_THRESHOLD = 60;
    private static final int FOLLOW_UP_CHAIN_LIMIT = 4;
    private static final int QUESTION_LIMIT = 180;
    private static final int ANSWER_LIMIT = 320;
    private static final int FEEDBACK_LIMIT = 180;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern MOBILE_PATTERN = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    private final InterviewSessionRuntimeSnapshotService runtimeSnapshotService;

    public InterviewHistoryContext load(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return InterviewHistoryContext.empty();
        }
        try {
            InterviewSessionRuntimeSnapshot snapshot = runtimeSnapshotService.findSnapshot(sessionId).orElse(null);
            List<InterviewTurnLog> turns = runtimeSnapshotService.loadPersistedTurns(sessionId);
            if (snapshot == null && (turns == null || turns.isEmpty())) {
                return InterviewHistoryContext.empty();
            }
            List<InterviewTurnLog> safeTurns = turns == null ? List.of() : turns;
            InterviewHistoryContext context = new InterviewHistoryContext(
                    true,
                    snapshot == null ? null : snapshot.getSnapshotVersion(),
                    snapshot == null ? null : snapshot.getArchiveWatermark(),
                    assessedQuestionNumbers(safeTurns),
                    recentTurns(safeTurns),
                    lowScoreFindings(safeTurns),
                    followUpChains(safeTurns),
                    uncoveredQuestionNumbers(snapshot, safeTurns)
            );
            log.debug("已生成面试历史上下文投影，sessionId={}，快照版本={}，归档轮次={}，最近轮次={}",
                    sessionId, context.snapshotVersion(), safeTurns.size(), context.recentTurns().size());
            return context;
        } catch (Exception ex) {
            log.warn("面试历史上下文暂不可用，将在无历史上下文条件下继续，sessionId={}，原因={}",
                    sessionId, ex.getMessage());
            return InterviewHistoryContext.empty();
        }
    }

    private List<String> assessedQuestionNumbers(List<InterviewTurnLog> turns) {
        Set<String> questionNumbers = new LinkedHashSet<>();
        for (InterviewTurnLog turn : turns) {
            String questionNumber = normalizedQuestionNumber(turn == null ? null : turn.getQuestionNumber());
            if (questionNumber != null) {
                questionNumbers.add(questionNumber);
            }
        }
        return List.copyOf(questionNumbers);
    }

    private List<InterviewHistoryContext.TurnSummary> recentTurns(List<InterviewTurnLog> turns) {
        int start = Math.max(0, turns.size() - RECENT_TURN_LIMIT);
        List<InterviewHistoryContext.TurnSummary> summaries = new ArrayList<>();
        for (InterviewTurnLog turn : turns.subList(start, turns.size())) {
            if (turn == null) {
                continue;
            }
            summaries.add(new InterviewHistoryContext.TurnSummary(
                    normalizedQuestionNumber(turn.getQuestionNumber()),
                    sanitize(turn.getQuestionContent(), QUESTION_LIMIT),
                    sanitize(turn.getAnswerContent(), ANSWER_LIMIT),
                    turn.getScore(),
                    sanitize(turn.getFeedback(), FEEDBACK_LIMIT),
                    Boolean.TRUE.equals(turn.getIsFollowUp()),
                    normalizedQuestionNumber(turn.getNextQuestionNumber())
            ));
        }
        return summaries;
    }

    private List<InterviewHistoryContext.ScoreFinding> lowScoreFindings(List<InterviewTurnLog> turns) {
        return turns.stream()
                .filter(turn -> turn != null
                        && turn.getScore() != null
                        && turn.getScore() <= LOW_SCORE_THRESHOLD
                        && !Boolean.TRUE.equals(turn.getIsFollowUp()))
                .sorted(Comparator.comparing(InterviewTurnLog::getScore))
                .limit(LOW_SCORE_LIMIT)
                .map(turn -> new InterviewHistoryContext.ScoreFinding(
                        normalizedQuestionNumber(turn.getQuestionNumber()),
                        turn.getScore(),
                        sanitize(turn.getFeedback(), FEEDBACK_LIMIT)))
                .toList();
    }

    private List<InterviewHistoryContext.FollowUpChain> followUpChains(List<InterviewTurnLog> turns) {
        Set<String> seen = new LinkedHashSet<>();
        List<InterviewHistoryContext.FollowUpChain> chains = new ArrayList<>();
        for (InterviewTurnLog turn : turns) {
            if (turn == null) {
                continue;
            }
            String rootQuestionNumber = normalizedQuestionNumber(turn.getQuestionNumber());
            String followUpQuestionNumber = normalizedQuestionNumber(turn.getNextQuestionNumber());
            if (rootQuestionNumber == null || followUpQuestionNumber == null || !followUpQuestionNumber.contains("-F")) {
                continue;
            }
            String key = rootQuestionNumber + "->" + followUpQuestionNumber;
            if (seen.add(key)) {
                chains.add(new InterviewHistoryContext.FollowUpChain(rootQuestionNumber, followUpQuestionNumber));
            }
            if (chains.size() >= FOLLOW_UP_CHAIN_LIMIT) {
                break;
            }
        }
        return List.copyOf(chains);
    }

    private List<String> uncoveredQuestionNumbers(InterviewSessionRuntimeSnapshot snapshot, List<InterviewTurnLog> turns) {
        if (snapshot == null || snapshot.getQuestions() == null || snapshot.getQuestions().isEmpty()) {
            return List.of();
        }
        Set<String> answeredPrimaryQuestions = new LinkedHashSet<>();
        for (InterviewTurnLog turn : turns) {
            String questionNumber = normalizedQuestionNumber(turn == null ? null : turn.getQuestionNumber());
            if (questionNumber != null) {
                answeredPrimaryQuestions.add(primaryQuestionNumber(questionNumber));
            }
        }
        List<String> uncovered = new ArrayList<>();
        for (String questionNumber : snapshot.getQuestions().keySet()) {
            String primaryQuestionNumber = primaryQuestionNumber(normalizedQuestionNumber(questionNumber));
            if (primaryQuestionNumber != null && !answeredPrimaryQuestions.contains(primaryQuestionNumber)) {
                uncovered.add(primaryQuestionNumber);
            }
        }
        return uncovered.stream().distinct().toList();
    }

    private String sanitize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String redacted = EMAIL_PATTERN.matcher(value).replaceAll("[已脱敏邮箱]");
        redacted = MOBILE_PATTERN.matcher(redacted).replaceAll("[已脱敏手机号]");
        String normalized = redacted.trim().replaceAll("\\s+", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String normalizedQuestionNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    private String primaryQuestionNumber(String questionNumber) {
        if (questionNumber == null) {
            return null;
        }
        int separator = questionNumber.indexOf("-F");
        return separator > 0 ? questionNumber.substring(0, separator) : questionNumber;
    }
}
