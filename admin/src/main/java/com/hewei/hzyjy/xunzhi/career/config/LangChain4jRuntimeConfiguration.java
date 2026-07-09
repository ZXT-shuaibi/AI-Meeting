package com.hewei.hzyjy.xunzhi.career.config;

import com.hewei.hzyjy.xunzhi.career.agent.cv.CvReview;
import com.hewei.hzyjy.xunzhi.career.agent.interview.InterviewPlan;
import com.hewei.hzyjy.xunzhi.career.agent.interview.InterviewStagePlan;
import com.hewei.hzyjy.xunzhi.career.agent.interview.JdAlignmentResult;
import com.hewei.hzyjy.xunzhi.career.agent.interview.ReflectionDecision;
import com.hewei.hzyjy.xunzhi.career.agent.interview.ReflectionResult;
import com.hewei.hzyjy.xunzhi.career.agent.interview.TechnicalQuestionSuggestion;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Configuration
public class LangChain4jRuntimeConfiguration {

    @Bean("CvReviewer")
    @ConditionalOnMissingBean(name = "CvReviewer")
    public LocalCvReviewerAgent cvReviewerAgent() {
        return new LocalCvReviewerAgent();
    }

    @Bean("ScoredCvTailor")
    @ConditionalOnMissingBean(name = "ScoredCvTailor")
    public LocalScoredCvTailorAgent scoredCvTailorAgent() {
        return new LocalScoredCvTailorAgent();
    }

    @Bean("JDAlignmentAgent")
    @ConditionalOnMissingBean(name = "JDAlignmentAgent")
    public LocalJdAlignmentAgent jdAlignmentAgent() {
        return new LocalJdAlignmentAgent();
    }

    @Bean("InterviewCoordinatorAgent")
    @ConditionalOnMissingBean(name = "InterviewCoordinatorAgent")
    public LocalInterviewCoordinatorAgent interviewCoordinatorAgent() {
        return new LocalInterviewCoordinatorAgent();
    }

    @Bean("InterviewOrchestratorService")
    @ConditionalOnMissingBean(name = "InterviewOrchestratorService")
    public LocalInterviewOrchestratorAgent interviewOrchestratorAgent() {
        return new LocalInterviewOrchestratorAgent();
    }

    @Bean("InterviewReflectorAgent")
    @ConditionalOnMissingBean(name = "InterviewReflectorAgent")
    public LocalInterviewReflectorAgent interviewReflectorAgent() {
        return new LocalInterviewReflectorAgent();
    }

    @Bean("JavaTechInterviewerAgent")
    @ConditionalOnMissingBean(name = "JavaTechInterviewerAgent")
    public LocalJavaTechInterviewerAgent javaTechInterviewerAgent() {
        return new LocalJavaTechInterviewerAgent();
    }

    public static class LocalCvReviewerAgent {
        public String review(CvBO cv, String jobDescription, List<String> referenceTemplates) {
            double score = score(cv, jobDescription);
            return "{\"score\":" + score + ",\"feedback\":\"" + escape(feedback(score)) + "\"}";
        }
    }

    public static class LocalScoredCvTailorAgent {
        public String tailor(CvBO cv, CvReview review, List<String> referenceTemplates) {
            String summary = safe(cv == null ? null : cv.getSummary());
            String advice = review == null ? "Improve JD alignment." : review.feedback();
            String optimized = summary.contains("Optimized for target JD")
                    ? summary
                    : summary + "\nOptimized for target JD: " + abbreviate(advice, 180);
            return "{\"title\":\"" + escape(firstNonBlank(cv == null ? null : cv.getTitle(), "Target Role"))
                    + "\",\"summary\":\"" + escape(optimized)
                    + "\",\"advice\":\"" + escape(advice) + "\"}";
        }
    }

    public static class LocalJdAlignmentAgent {
        public JdAlignmentResult align(String memoryId, CvBO cv, String jobDescription) {
            List<String> matched = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            String cvText = String.valueOf(cv).toLowerCase(Locale.ROOT);
            for (String token : tokenize(jobDescription)) {
                if (cvText.contains(token)) {
                    matched.add(token);
                } else {
                    missing.add(token);
                }
            }
            int total = matched.size() + missing.size();
            double score = total == 0 ? 0.5 : Math.min(1.0, (double) matched.size() / total);
            return JdAlignmentResult.builder()
                    .matchScore(score)
                    .matchedSkills(matched.stream().distinct().limit(12).toList())
                    .missingSkills(missing.stream().distinct().limit(12).toList())
                    .summary("Local JD alignment fallback, matched=" + matched.size() + ", missing=" + missing.size())
                    .build();
        }
    }

    public static class LocalInterviewCoordinatorAgent {
        public List<InterviewStagePlan> coordinate(String memoryId, JdAlignmentResult alignment) {
            List<String> seeds = alignment == null || alignment.matchedSkills() == null || alignment.matchedSkills().isEmpty()
                    ? List.of("project architecture", "backend fundamentals")
                    : alignment.matchedSkills();
            return List.of(
                    InterviewStagePlan.builder().stageName("JD_ALIGNMENT").goal("Validate JD fit").questionSeeds(seeds).build(),
                    InterviewStagePlan.builder().stageName("TECH_DEPTH").goal("Probe implementation depth").questionSeeds(List.of("Spring", "Redis", "MySQL")).build(),
                    InterviewStagePlan.builder().stageName("REFLECTION").goal("Verify ownership and tradeoffs").questionSeeds(List.of("incident", "metric", "tradeoff")).build()
            );
        }
    }

    public static class LocalInterviewOrchestratorAgent {
        public InterviewPlan plan(String sessionId, CvBO cv, String jobDescription, JdAlignmentResult alignment, List<InterviewStagePlan> stages, String firstQuestion) {
            return InterviewPlan.builder()
                    .sessionId(sessionId)
                    .alignment(alignment)
                    .stages(stages == null ? List.of() : stages)
                    .firstQuestion(firstNonBlank(firstQuestion, "Please introduce your most relevant project."))
                    .build();
        }
    }

    public static class LocalInterviewReflectorAgent {
        public ReflectionResult reflect(String memoryId, String currentQuestion, String userAnswer, CvBO cv, Object memoryView) {
            int score = answerScore(userAnswer);
            ReflectionDecision decision = score < 6 ? ReflectionDecision.PROBE : ReflectionDecision.NEXT;
            return ReflectionResult.builder()
                    .score(score)
                    .decision(decision)
                    .feedback(score < 6 ? "Answer lacks concrete evidence." : "Answer has enough structure for next step.")
                    .probeSuggestions(score < 6 ? List.of("Ask for concrete metrics", "Ask for failure handling details") : List.of())
                    .build();
        }
    }

    public static class LocalJavaTechInterviewerAgent {
        public TechnicalQuestionSuggestion generateQuestion(
                String memoryId,
                CvBO cv,
                String jobDescription,
                JdAlignmentResult alignment,
                List<InterviewStagePlan> stages,
                String skillContext) {
            String seed = alignment == null || alignment.matchedSkills() == null || alignment.matchedSkills().isEmpty()
                    ? firstStageSeed(stages)
                    : alignment.matchedSkills().get(0);
            String question = "Please explain a Java backend project where you used " + firstNonBlank(seed, "distributed systems")
                    + ", including architecture, failure handling, metrics, and the tradeoffs you made.";
            return TechnicalQuestionSuggestion.builder()
                    .question(question)
                    .rationale("Local JavaTechInterviewer planning suggestion only; AI-Meeting executes the interview.")
                    .build();
        }
    }

    private static String firstStageSeed(List<InterviewStagePlan> stages) {
        if (stages == null) {
            return "";
        }
        return stages.stream()
                .filter(stage -> stage.questionSeeds() != null && !stage.questionSeeds().isEmpty())
                .map(stage -> stage.questionSeeds().get(0))
                .findFirst()
                .orElse("");
    }

    private static double score(CvBO cv, String jobDescription) {
        String cvText = String.valueOf(cv).toLowerCase(Locale.ROOT);
        List<String> tokens = tokenize(jobDescription);
        if (tokens.isEmpty()) {
            return 0.5;
        }
        long hits = tokens.stream().filter(cvText::contains).count();
        return Math.min(0.95, 0.45 + 0.5 * hits / tokens.size());
    }

    private static List<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : value.toLowerCase(Locale.ROOT).split("[^\\p{IsHan}\\p{Alnum}]+")) {
            if (token.length() >= 3) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static int answerScore(String answer) {
        if (answer == null || answer.isBlank()) {
            return 0;
        }
        int score = Math.min(10, Math.max(3, answer.length() / 80));
        if (answer.matches("(?s).*(because|therefore|tradeoff|metric|QPS|latency|failure|incident|index).*")) {
            score = Math.min(10, score + 2);
        }
        return score;
    }

    private static String feedback(double score) {
        return score > 0.8 ? "Resume passes the quality gate." : "Improve JD keyword coverage and add quantified project evidence.";
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String escape(String value) {
        return safe(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
