package com.hewei.hzyjy.xunzhi.career.agent.interview;

import com.hewei.hzyjy.xunzhi.career.agent.support.AgentResponseParser;
import com.hewei.hzyjy.xunzhi.career.ai.AgentRuntimeGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import com.hewei.hzyjy.xunzhi.career.memory.CompactedMemoryView;
import com.hewei.hzyjy.xunzhi.career.memory.HybridCompactingChatMemory;
import com.hewei.hzyjy.xunzhi.career.memory.MemoryMessage;
import com.hewei.hzyjy.xunzhi.career.memory.MemoryRole;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.skill.CareerSkillRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class InterviewPlanningService {

    private final AiGateway aiGateway;
    private final HybridCompactingChatMemory chatMemory;
    private final ObjectProvider<AgentRuntimeGateway> agentRuntimeGatewayProvider;
    private final CareerSkillRegistry skillRegistry;

    @Autowired
    public InterviewPlanningService(
            AiGateway aiGateway,
            HybridCompactingChatMemory chatMemory,
            ObjectProvider<AgentRuntimeGateway> agentRuntimeGatewayProvider,
            ObjectProvider<CareerSkillRegistry> skillRegistryProvider) {
        this.aiGateway = aiGateway;
        this.chatMemory = chatMemory;
        this.agentRuntimeGatewayProvider = agentRuntimeGatewayProvider;
        this.skillRegistry = skillRegistryProvider.getIfAvailable(CareerSkillRegistry::disabled);
    }

    public InterviewPlanningService(AiGateway aiGateway, HybridCompactingChatMemory chatMemory) {
        this.aiGateway = aiGateway;
        this.chatMemory = chatMemory;
        this.agentRuntimeGatewayProvider = null;
        this.skillRegistry = CareerSkillRegistry.disabled();
    }

    public InterviewPlanningService(AiGateway aiGateway, HybridCompactingChatMemory chatMemory, ObjectProvider<AgentRuntimeGateway> agentRuntimeGatewayProvider) {
        this.aiGateway = aiGateway;
        this.chatMemory = chatMemory;
        this.agentRuntimeGatewayProvider = agentRuntimeGatewayProvider;
        this.skillRegistry = CareerSkillRegistry.disabled();
    }

    public InterviewPlanningService(AiGateway aiGateway, HybridCompactingChatMemory chatMemory, CareerSkillRegistry skillRegistry) {
        this.aiGateway = aiGateway;
        this.chatMemory = chatMemory;
        this.agentRuntimeGatewayProvider = null;
        this.skillRegistry = skillRegistry == null ? CareerSkillRegistry.disabled() : skillRegistry;
    }

    public InterviewPlan plan(String sessionId, CvBO cv, String jobDescription) {
        return plan(memoryId(sessionId, cv), sessionId, cv, jobDescription);
    }

    public InterviewPlan plan(String memoryId, String sessionId, CvBO cv, String jobDescription) {
        String safeMemoryId = memoryId == null || memoryId.isBlank() ? memoryId(sessionId, cv) : memoryId;
        JdAlignmentResult alignment = align(safeMemoryId, cv, jobDescription);
        List<InterviewStagePlan> stages = coordinateStages(safeMemoryId, alignment);
        FirstQuestionCandidate firstQuestion = generateFirstQuestion(safeMemoryId, cv, jobDescription, alignment, stages);
        InterviewPlan plan = tryLangChain4jPlan(sessionId, cv, jobDescription, alignment, stages, firstQuestion.question());
        if (plan != null && firstQuestion.javaTechGenerated()) {
            plan = InterviewPlan.builder()
                    .sessionId(plan.sessionId())
                    .alignment(plan.alignment())
                    .stages(plan.stages())
                    .firstQuestion(firstQuestion.question())
                    .build();
        }
        if (plan == null) {
            plan = InterviewPlan.builder()
                    .sessionId(sessionId)
                    .alignment(alignment)
                    .stages(stages)
                    .firstQuestion(firstQuestion.question())
                    .build();
        }
        chatMemory.add(safeMemoryId, MemoryMessage.builder()
                .role(MemoryRole.ASSISTANT)
                .content("Plan-Execute-Reflect plan decision: " + plan)
                .metadata(Map.of("scene", "INTERVIEW_COORDINATION"))
                .build());
        return plan;
    }

    public ReflectionResult reflect(String memoryId, String currentQuestion, String userAnswer, CvBO cv) {
        String safeMemoryId = memoryId == null || memoryId.isBlank() ? memoryId(null, cv) : memoryId;
        CompactedMemoryView memoryView = chatMemory.view(safeMemoryId, 8);
        ReflectionResult agentResult = tryLangChain4jReflect(safeMemoryId, currentQuestion, userAnswer, cv, memoryView);
        if (isValidReflection(agentResult)) {
            return agentResult;
        }
        String feedback = aiGateway.chat(AiPromptRequest.builder()
                .sceneCode("INTERVIEW_REFLECTION")
                .sessionId(safeMemoryId)
                .systemPrompt("Evaluate answer quality and return JSON: {\"score\":0-10,\"decision\":\"PROBE|NEXT|STAGE_FINISH|FINISH\",\"feedback\":\"...\",\"probeSuggestions\":[\"...\"]}.\n"
                        + runtimeSkill("question-probing"))
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
        JdAlignmentResult agentResult = tryLangChain4jAlignment(memoryId, cv, jobDescription);
        if (agentResult != null) {
            chatMemory.add(memoryId, MemoryMessage.builder()
                    .role(MemoryRole.ASSISTANT)
                    .content("JD alignment decision: " + agentResult)
                    .metadata(Map.of("scene", "JD_ALIGNMENT", "runtime", "langchain4j"))
                    .build());
            return agentResult;
        }
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
                .systemPrompt("Summarize JD-resume alignment for interview planning. Highlight matched skills, missing risks, and first probe direction.\n"
                        + runtimeSkill("jd-alignment"))
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
        List<InterviewStagePlan> agentStages = tryLangChain4jStages(memoryId, alignment);
        if (!agentStages.isEmpty()) {
            chatMemory.add(memoryId, MemoryMessage.builder()
                    .role(MemoryRole.ASSISTANT)
                    .content("Interview coordination stages: " + agentStages)
                    .metadata(Map.of("scene", "INTERVIEW_COORDINATION", "runtime", "langchain4j"))
                    .build());
            return agentStages;
        }
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

    private FirstQuestionCandidate generateFirstQuestion(String memoryId, CvBO cv, String jobDescription, JdAlignmentResult alignment, List<InterviewStagePlan> stages) {
        TechnicalQuestionSuggestion agentQuestion = tryJavaTechInterviewerQuestion(memoryId, cv, jobDescription, alignment, stages);
        if (agentQuestion != null && !safe(agentQuestion.question()).isBlank()) {
            chatMemory.add(memoryId, MemoryMessage.builder()
                    .role(MemoryRole.ASSISTANT)
                    .content("JavaTechInterviewer question suggestion: " + agentQuestion)
                    .metadata(Map.of("scene", "INTERVIEW_COORDINATION", "agent", "JavaTechInterviewerAgent"))
                    .build());
            return new FirstQuestionCandidate(agentQuestion.question(), true);
        }
        String seed = alignment.matchedSkills().isEmpty() ? "your most relevant backend project" : alignment.matchedSkills().get(0);
        return new FirstQuestionCandidate(
                "Please explain one project where you used " + seed + " and describe the architecture, your responsibility, and measurable impact.",
                false
        );
    }

    private TechnicalQuestionSuggestion tryJavaTechInterviewerQuestion(
            String memoryId,
            CvBO cv,
            String jobDescription,
            JdAlignmentResult alignment,
            List<InterviewStagePlan> stages) {
        AgentRuntimeGateway gateway = agentRuntimeGateway();
        if (gateway == null) {
            return null;
        }
        try {
            return gateway.invoke("JavaTechInterviewerAgent", "generateQuestion", Map.of(
                    "memoryId", memoryId,
                    "cv", cv == null ? CvBO.builder().build() : cv,
                    "jobDescription", safe(jobDescription),
                    "alignment", alignment == null ? JdAlignmentResult.builder().build() : alignment,
                    "stages", stages == null ? List.of() : stages,
                    "skillContext", runtimeSkill("question-probing")
            ), TechnicalQuestionSuggestion.class);
        } catch (Exception ex) {
            log.debug("LangChain4j JavaTechInterviewerAgent unavailable, falling back", ex);
            return null;
        }
    }

    private JdAlignmentResult tryLangChain4jAlignment(String memoryId, CvBO cv, String jobDescription) {
        AgentRuntimeGateway gateway = agentRuntimeGateway();
        if (gateway == null) {
            return null;
        }
        try {
            return gateway.invoke("JDAlignmentAgent", "align", Map.of(
                    "memoryId", memoryId,
                    "cv", cv,
                    "jobDescription", jobDescription
            ), JdAlignmentResult.class);
        } catch (Exception ex) {
            log.debug("LangChain4j JDAlignmentAgent unavailable, falling back", ex);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<InterviewStagePlan> tryLangChain4jStages(String memoryId, JdAlignmentResult alignment) {
        AgentRuntimeGateway gateway = agentRuntimeGateway();
        if (gateway == null) {
            return List.of();
        }
        try {
            Object result = gateway.invoke("InterviewCoordinatorAgent", "coordinate", Map.of(
                    "memoryId", memoryId,
                    "alignment", alignment
            ), Object.class);
            if (result instanceof List<?> list) {
                return list.stream()
                        .filter(InterviewStagePlan.class::isInstance)
                        .map(InterviewStagePlan.class::cast)
                        .toList();
            }
            List<InterviewStagePlan> wrappedStages = extractStages(result);
            if (!wrappedStages.isEmpty()) {
                return wrappedStages;
            }
            return List.of();
        } catch (Exception ex) {
            log.debug("LangChain4j InterviewCoordinatorAgent unavailable, falling back", ex);
            return List.of();
        }
    }

    private InterviewPlan tryLangChain4jPlan(
            String sessionId,
            CvBO cv,
            String jobDescription,
            JdAlignmentResult alignment,
            List<InterviewStagePlan> stages,
            String firstQuestion) {
        AgentRuntimeGateway gateway = agentRuntimeGateway();
        if (gateway == null) {
            return null;
        }
        try {
            return gateway.invoke("InterviewOrchestratorService", "plan", Map.of(
                    "sessionId", sessionId == null ? "" : sessionId,
                    "cv", cv,
                    "jobDescription", jobDescription,
                    "alignment", alignment,
                    "stages", stages,
                    "firstQuestion", firstQuestion
            ), InterviewPlan.class);
        } catch (Exception ex) {
            log.debug("LangChain4j InterviewOrchestratorService unavailable, falling back", ex);
            return null;
        }
    }

    private ReflectionResult tryLangChain4jReflect(String memoryId, String currentQuestion, String userAnswer, CvBO cv, CompactedMemoryView memoryView) {
        AgentRuntimeGateway gateway = agentRuntimeGateway();
        if (gateway == null) {
            return null;
        }
        try {
            return gateway.invoke("InterviewReflectorAgent", "reflect", Map.of(
                    "memoryId", memoryId,
                    "currentQuestion", currentQuestion,
                    "userAnswer", userAnswer,
                    "cv", cv,
                    "memoryView", formatMemoryView(memoryView)
            ), ReflectionResult.class);
        } catch (Exception ex) {
            log.debug("LangChain4j InterviewReflectorAgent unavailable, falling back", ex);
            return null;
        }
    }

    private boolean isValidReflection(ReflectionResult result) {
        return result != null
                && result.decision() != null
                && result.score() >= 0
                && result.score() <= 10
                && !safe(result.feedback()).isBlank();
    }

    private AgentRuntimeGateway agentRuntimeGateway() {
        return agentRuntimeGatewayProvider == null ? null : agentRuntimeGatewayProvider.getIfAvailable();
    }

    private String runtimeSkill(String name) {
        return skillRegistry == null ? "" : skillRegistry.promptSection(name);
    }

    private String formatMemoryView(CompactedMemoryView memoryView) {
        if (memoryView == null) {
            return "";
        }
        String messages = memoryView.messages() == null ? "" : memoryView.messages().stream()
                .map(message -> message.role() + ": " + safe(message.content()))
                .limit(12)
                .collect(java.util.stream.Collectors.joining("\n"));
        return safe(memoryView.decisionContext()) + "\n" + messages;
    }

    private List<InterviewStagePlan> extractStages(Object result) {
        if (result == null) {
            return List.of();
        }
        try {
            Object stages = result.getClass().getMethod("stages").invoke(result);
            if (stages instanceof List<?> list) {
                return list.stream()
                        .filter(InterviewStagePlan.class::isInstance)
                        .map(InterviewStagePlan.class::cast)
                        .toList();
            }
        } catch (Exception ignored) {
            // External Agentic profile may return a wrapper; default fallback remains local.
        }
        return List.of();
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

    private record FirstQuestionCandidate(String question, boolean javaTechGenerated) {
    }
}
