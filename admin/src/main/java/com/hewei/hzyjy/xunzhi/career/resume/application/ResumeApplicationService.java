package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.hewei.hzyjy.xunzhi.career.agent.cv.CvOptimizationOrchestrator;
import com.hewei.hzyjy.xunzhi.career.agent.cv.CvOptimizationResult;
import com.hewei.hzyjy.xunzhi.career.agent.interview.InterviewPlan;
import com.hewei.hzyjy.xunzhi.career.agent.interview.InterviewPlanningService;
import com.hewei.hzyjy.xunzhi.career.agent.interview.ReflectionResult;
import com.hewei.hzyjy.xunzhi.career.memory.HybridCompactingChatMemory;
import com.hewei.hzyjy.xunzhi.career.memory.MemoryMessage;
import com.hewei.hzyjy.xunzhi.career.memory.MemoryRole;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.SkillBO;
import com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeChunk;
import com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeApplicationService {

    private final ResumeStore resumeStore;
    private final JobMatchTaskStore jobMatchTaskStore;
    private final ResumeRagService resumeRagService;
    private final CvOptimizationOrchestrator cvOptimizationOrchestrator;
    private final InterviewPlanningService interviewPlanningService;
    private final HybridCompactingChatMemory chatMemory;

    public ResumeUploadResult upload(Long userId, MultipartFile file) {
        CvBO parsed = parseUpload(userId, file);
        CvBO saved = resumeStore.save(parsed);
        chatMemory.add(memoryId(saved.getId()), MemoryMessage.builder()
                .role(MemoryRole.USER)
                .content("Resume uploaded and parsed: " + saved.getName() + " / " + saved.getTitle())
                .metadata(Map.of("scene", "RESUME_ANALYSIS", "resumeId", String.valueOf(saved.getId())))
                .build());
        return new ResumeUploadResult(saved.getId(), saved);
    }

    public ResumeEmbeddingResult embedding(Long resumeId) {
        CvBO cv = getResume(resumeId);
        List<ResumeChunk> chunks = resumeRagService.storeCvBO(cv);
        chatMemory.add(memoryId(resumeId), MemoryMessage.builder()
                .role(MemoryRole.TOOL)
                .content("Resume embedding completed. chunkCount=" + chunks.size())
                .metadata(Map.of("scene", "RESUME_ANALYSIS", "resumeId", String.valueOf(resumeId)))
                .build());
        return new ResumeEmbeddingResult(resumeId, chunks.size(), chunks);
    }

    public JobMatchTaskResult matchResumes(String jobDescription, int limit) {
        String taskId = UUID.randomUUID().toString();
        int boundedLimit = limit <= 0 ? 3 : limit;
        JobMatchTaskResult started = new JobMatchTaskResult(taskId, "STARTED", List.of(), null);
        jobMatchTaskStore.save(started, jobDescription, boundedLimit, null);
        try {
            List<String> templates = resumeRagService.retrieveTemplates(jobDescription, boundedLimit);
            JobMatchTaskResult completed = new JobMatchTaskResult(taskId, "COMPLETED", templates, null);
            return jobMatchTaskStore.save(completed, jobDescription, boundedLimit, null);
        } catch (Exception ex) {
            log.warn("Career job match task failed. taskId={}", taskId, ex);
            JobMatchTaskResult failed = new JobMatchTaskResult(taskId, "FAILED", List.of(), ex.getMessage());
            return jobMatchTaskStore.save(failed, jobDescription, boundedLimit, ex.getMessage());
        }
    }

    public JobMatchTaskResult getMatchTask(String taskId) {
        return jobMatchTaskStore.findByTaskId(taskId).orElseGet(() -> JobMatchTaskResult.notFound(taskId));
    }

    public CvOptimizationResult optimize(Long resumeId, String jobDescription) {
        CvBO cv = getResume(resumeId);
        List<String> templates = resumeRagService.retrieveTemplates(jobDescription, 3);
        CvOptimizationResult result = cvOptimizationOrchestrator.optimize(cv, jobDescription, templates, 3);
        resumeStore.save(result.cv());
        chatMemory.add(memoryId(resumeId), MemoryMessage.builder()
                .role(MemoryRole.ASSISTANT)
                .content("Resume optimization decision: scoreGatePassed=" + result.scoreGatePassed()
                        + ", iterations=" + result.iterations()
                        + ", bestReview=" + result.bestReview())
                .metadata(Map.of("scene", "RESUME_TAILOR", "resumeId", String.valueOf(resumeId)))
                .build());
        return result;
    }

    public InterviewPlan planInterview(String sessionId, Long resumeId, String jobDescription) {
        InterviewPlan plan = interviewPlanningService.plan(sessionId, getResume(resumeId), jobDescription);
        chatMemory.add(memoryId(sessionId, resumeId), MemoryMessage.builder()
                .role(MemoryRole.ASSISTANT)
                .content("Interview plan generated. firstQuestion=" + plan.firstQuestion() + ", alignment=" + plan.alignment())
                .metadata(Map.of("scene", "INTERVIEW_COORDINATION", "resumeId", String.valueOf(resumeId)))
                .build());
        return plan;
    }

    public ReflectionResult reflect(String sessionId, Long resumeId, String currentQuestion, String userAnswer) {
        String memoryId = memoryId(sessionId, resumeId);
        chatMemory.add(memoryId, MemoryMessage.builder()
                .role(MemoryRole.USER)
                .content("Question: " + currentQuestion + "\nAnswer: " + userAnswer)
                .metadata(Map.of("scene", "INTERVIEW_REFLECTION", "resumeId", String.valueOf(resumeId)))
                .build());
        ReflectionResult result = interviewPlanningService.reflect(memoryId, currentQuestion, userAnswer, getResume(resumeId));
        chatMemory.add(memoryId, MemoryMessage.builder()
                .role(MemoryRole.ASSISTANT)
                .content("Reflect decision: " + result.decision() + ", score=" + result.score() + ", feedback=" + result.feedback())
                .metadata(Map.of("scene", "INTERVIEW_REFLECTION", "resumeId", String.valueOf(resumeId)))
                .build());
        return result;
    }

    public ReflectionResult reflect(Long resumeId, String currentQuestion, String userAnswer) {
        return reflect(null, resumeId, currentQuestion, userAnswer);
    }

    private CvBO getResume(Long resumeId) {
        return resumeStore.findById(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found: " + resumeId));
    }

    private CvBO parseUpload(Long userId, MultipartFile file) {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        String content = "";
        try {
            if (file != null) {
                content = new String(file.getBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            content = originalFilename == null ? "" : originalFilename;
        }
        String summary = content.isBlank() ? "Uploaded resume: " + originalFilename : content;
        return CvBO.builder()
                .userId(userId)
                .cvType("upload")
                .name(originalFilename == null ? "uploaded-resume" : originalFilename)
                .title(inferTitle(summary))
                .summary(summary)
                .skills(inferSkills(summary))
                .build();
    }

    private String inferTitle(String content) {
        String lower = content.toLowerCase();
        if (lower.contains("java")) {
            return "Java Backend Engineer";
        }
        if (lower.contains("frontend") || lower.contains("react") || lower.contains("vue")) {
            return "Frontend Engineer";
        }
        return "Candidate";
    }

    private List<SkillBO> inferSkills(String content) {
        String lower = content.toLowerCase();
        return List.of("java", "spring", "redis", "mysql", "rag", "langchain4j", "spring ai").stream()
                .filter(lower::contains)
                .map(skill -> SkillBO.builder().name(skill).level("experienced").build())
                .toList();
    }

    private String memoryId(Long resumeId) {
        return "resume:" + resumeId;
    }

    private String memoryId(String sessionId, Long resumeId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return "interview:" + sessionId;
        }
        return memoryId(resumeId);
    }
}
