package com.hewei.hzyjy.xunzhi.career.agent.interview;

import com.hewei.hzyjy.xunzhi.career.agent.support.AgentResponseParser;
import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import com.hewei.hzyjy.xunzhi.career.memory.CompactedMemoryView;
import com.hewei.hzyjy.xunzhi.career.memory.HybridCompactingChatMemory;
import com.hewei.hzyjy.xunzhi.career.memory.MemoryMessage;
import com.hewei.hzyjy.xunzhi.career.memory.MemoryRole;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InterviewPlanningService {

    private final AiGateway aiGateway;
    private final HybridCompactingChatMemory chatMemory;

    public InterviewPlan plan(String sessionId, CvBO cv, String jobDescription) {
        String memoryId = memoryId(sessionId, cv);
        JdAlignmentResult alignment = align(memoryId, cv, jobDescription);
        List<InterviewStagePlan> stages = coordinateStages(memoryId, alignment);
        String firstQuestion = generateFirstQuestion(jobDescription, alignment);
        InterviewPlan plan = InterviewPlan.builder()
                .sessionId(sessionId)
                .alignment(alignment)
                .stages(stages)
                .firstQuestion(firstQuestion)
                .build();
        chatMemory.add(memoryId, MemoryMessage.builder()
                .role(MemoryRole.ASSISTANT)
                .content("Plan-Execute-Reflect plan decision: " + plan)
                .metadata(Map.of("scene", "INTERVIEW_COORDINATION"))
                .build());
        return plan;
    }

    public ReflectionResult reflect(String memoryId, String currentQuestion, String userAnswer, CvBO cv) {
        String safeMemoryId = memoryId == null || memoryId.isBlank() ? memoryId(null, cv) : memoryId;
        CompactedMemoryView memoryView = chatMemory.view(safeMemoryId, 8);
        String feedback = aiGateway.chat(AiPromptRequest.builder()
                .sceneCode("INTERVIEW_REFLECTION")
                .sessionId(safeMemoryId)
                .systemPrompt("Evaluate answer quality and return JSON: {\"score\":0-10,\"decision\":\"PROBE|NEXT|STAGE_FINISH|FINISH\",\"feedback\":\"...\",\"probeSuggestions\":[\"...\"]}.")
                .userPrompt(memoryView.decisionContext()
                        + "\nQuestion:\n" + currentQuestion
                        + "\n\nAnswer:\n" + userAnswer
                        + "\n\nCV:\n" + cv)
                .build()).content();
        double normalizedScore = AgentResponseParser.score(feedback).orElseGet(() -> heuristicAnswerScore(userAnswer) / 10.0);
        int score = (int) Math.round(normalizedScore * 10);
        ReflectionDecision decision = parseDecision(feedback, score);
        return ReflectionResult.builder()
                .score(score)
                .decision(decision)
                .feedback(AgentResponseParser.feedback(feedback).orElse(feedback))
                .probeSuggestions(decision == ReflectionDecision.PROBE
                        ? List.of("Ask for concrete project example", "Ask for failure handling details")
                        : List.of())
                .build();
    }

    public ReflectionResult reflect(String currentQuestion, String userAnswer, CvBO cv) {
        return reflect(memoryId(null, cv), currentQuestion, userAnswer, cv);
    }

    private JdAlignmentResult align(String memoryId, CvBO cv, String jobDescription) {
        String cvText = String.valueOf(cv).toLowerCase();
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String token : safe(jobDescription).toLowerCase().split("[^\\p{IsHan}\\p{Alnum}]+")) {
            if (token.length() < 3) {
                continue;
            }
            if (cvText.contains(token)) {
                matched.add(token);
            } else {
                missing.add(token);
            }
        }
        int total = matched.size() + missing.size();
        double score = total == 0 ? 0.5 : Math.min(1.0, (double) matched.size() / total);
        String summary = aiGateway.chat(AiPromptRequest.builder()
                .sceneCode("JD_ALIGNMENT")
                .sessionId(memoryId)
                .systemPrompt("Summarize JD-resume alignment for interview planning. Highlight matched skills, missing risks, and first probe direction.")
                .userPrompt(chatMemory.view(memoryId, 5).decisionContext() + "\nJD:\n" + jobDescription + "\n\nCV:\n" + cv)
                .build()).content();
        JdAlignmentResult result = JdAlignmentResult.builder()
                .matchScore(score)
                .matchedSkills(matched.stream().distinct().limit(12).toList())
                .missingSkills(missing.stream().distinct().limit(12).toList())
                .summary(summary)
                .build();
        chatMemory.add(memoryId, MemoryMessage.builder()
                .role(MemoryRole.ASSISTANT)
                .content("JD alignment decision: " + result)
                .metadata(Map.of("scene", "JD_ALIGNMENT"))
                .build());
        return result;
    }

    private List<InterviewStagePlan> coordinateStages(String memoryId, JdAlignmentResult alignment) {
        List<String> matchedSeeds = alignment.matchedSkills().isEmpty()
                ? List.of("project architecture", "backend fundamentals")
                : alignment.matchedSkills();
        List<InterviewStagePlan> stages = List.of(
                InterviewStagePlan.builder()
                        .stageName("JD_ALIGNMENT")
                        .goal("Validate must-have skills and project relevance")
                        .questionSeeds(matchedSeeds)
                        .build(),
                InterviewStagePlan.builder()
                        .stageName("JAVA_TECH_DEPTH")
                        .goal("Probe core backend design, middleware and system reliability")
                        .questionSeeds(List.of("Spring Boot", "Redis", "MySQL", "distributed systems"))
                        .build(),
                InterviewStagePlan.builder()
                        .stageName("PROJECT_REFLECTION")
                        .goal("Verify project ownership and quantified impact")
                        .questionSeeds(List.of("architecture tradeoff", "production incident", "performance optimization"))
                        .build()
        );
        chatMemory.add(memoryId, MemoryMessage.builder()
                .role(MemoryRole.ASSISTANT)
                .content("Interview coordination stages: " + stages)
                .metadata(Map.of("scene", "INTERVIEW_COORDINATION"))
                .build());
        return stages;
    }

    private String generateFirstQuestion(String jobDescription, JdAlignmentResult alignment) {
        String seed = alignment.matchedSkills().isEmpty() ? "your most relevant backend project" : alignment.matchedSkills().get(0);
        return "Please explain one project where you used " + seed + " and describe the architecture, your responsibility, and measurable impact.";
    }

    private ReflectionDecision parseDecision(String feedback, int score) {
        return AgentResponseParser.decision(feedback)
                .flatMap(value -> {
                    try {
                        return java.util.Optional.of(ReflectionDecision.valueOf(value));
                    } catch (IllegalArgumentException ex) {
                        return java.util.Optional.empty();
                    }
                })
                .orElseGet(() -> heuristicDecision(feedback, score));
    }

    private ReflectionDecision heuristicDecision(String feedback, int score) {
        String lower = safe(feedback).toLowerCase();
        if (lower.contains("finish") || lower.contains("stage_finish")) {
            return lower.contains("stage_finish") ? ReflectionDecision.STAGE_FINISH : ReflectionDecision.FINISH;
        }
        return score < 6 ? ReflectionDecision.PROBE : ReflectionDecision.NEXT;
    }

    private int heuristicAnswerScore(String userAnswer) {
        if (userAnswer == null || userAnswer.isBlank()) {
            return 0;
        }
        int score = Math.min(10, Math.max(3, userAnswer.length() / 80));
        if (userAnswer.matches("(?s).*(because|therefore|so|tradeoff|QPS|latency|metric|index|failure|incident).*")) {
            score = Math.min(10, score + 2);
        }
        return score;
    }

    private String memoryId(String sessionId, CvBO cv) {
        if (sessionId != null && !sessionId.isBlank()) {
            return "interview:" + sessionId;
        }
        Long resumeId = cv == null ? null : cv.getId();
        return "resume:" + resumeId;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
