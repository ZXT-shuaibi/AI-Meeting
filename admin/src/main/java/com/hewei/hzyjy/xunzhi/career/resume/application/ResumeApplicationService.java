package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.alibaba.fastjson2.JSON;
import com.hewei.hzyjy.xunzhi.career.agent.cv.CvOptimizationOrchestrator;
import com.hewei.hzyjy.xunzhi.career.agent.cv.CvOptimizationResult;
import com.hewei.hzyjy.xunzhi.career.harness.application.AgentRunCoordinator;
import com.hewei.hzyjy.xunzhi.career.harness.model.AgentRunStartCommand;
import com.hewei.hzyjy.xunzhi.career.agent.cv.CvReview;
import com.hewei.hzyjy.xunzhi.career.agent.interview.CareerInterviewExecutionBridge;
import com.hewei.hzyjy.xunzhi.career.agent.interview.InterviewPlan;
import com.hewei.hzyjy.xunzhi.career.agent.interview.InterviewPlanningService;
import com.hewei.hzyjy.xunzhi.career.agent.interview.ReflectionResult;
import com.hewei.hzyjy.xunzhi.career.memory.HybridCompactingChatMemory;
import com.hewei.hzyjy.xunzhi.career.memory.MemoryMessage;
import com.hewei.hzyjy.xunzhi.career.memory.MemoryRole;
import com.hewei.hzyjy.xunzhi.career.observability.AiToolExecutionEvent;
import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.raglab.application.RagResumeAutoTagService;
import com.hewei.hzyjy.xunzhi.career.resume.model.SkillBO;
import com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeChunk;
import com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagMatch;
import com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagService;
import com.hewei.hzyjy.xunzhi.career.resume.render.ResumeRenderArtifact;
import com.hewei.hzyjy.xunzhi.career.resume.render.ResumeRenderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@Service
public class ResumeApplicationService {

    private static final Pattern XML_TAG = Pattern.compile("<[^>]+>");
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md", "markdown", "json", "csv", "log");
    private static final int MAX_RESUME_TEXT_LENGTH = 120000;
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DOCX_ENTRY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DOCX_TOTAL_UNCOMPRESSED_BYTES = 4 * 1024 * 1024;
    private static final int MAX_DOCX_ENTRIES = 128;
    private static final int MAX_JD_LENGTH = 12000;
    private static final int MAX_QUESTION_LENGTH = 4000;
    private static final int MAX_ANSWER_LENGTH = 12000;

    private final ResumeStore resumeStore;
    private final JobMatchTaskStore jobMatchTaskStore;
    private final ResumeParseTaskStore resumeParseTaskStore;
    private final ResumeRagService resumeRagService;
    private final CvOptimizationOrchestrator cvOptimizationOrchestrator;
    private final InterviewPlanningService interviewPlanningService;
    private final CareerInterviewExecutionBridge interviewExecutionBridge;
    private final HybridCompactingChatMemory chatMemory;
    private final ResumeStructuringService resumeStructuringService;
    private final ResumePdfTextExtractor resumePdfTextExtractor;
    private final ResumeRenderService resumeRenderService;
    private final TaskExecutor careerTaskExecutor;
    private final ObjectProvider<AiTracePublisher> tracePublisherProvider;
    private final ResumeObjectStorage resumeObjectStorage;
    private final RagResumeAutoTagService ragResumeAutoTagService;

    /**
     * Harness 运行审计为旁路能力：本地旧测试和未执行迁移脚本的环境均不应因此阻断简历优化主流程。
     */
    @Autowired(required = false)
    private AgentRunCoordinator agentRunCoordinator;
    private final ConcurrentMap<Long, Boolean> embeddedResumeIds = new ConcurrentHashMap<>();

    @Autowired
    public ResumeApplicationService(
            ResumeStore resumeStore,
            JobMatchTaskStore jobMatchTaskStore,
            ResumeParseTaskStore resumeParseTaskStore,
            ResumeRagService resumeRagService,
            CvOptimizationOrchestrator cvOptimizationOrchestrator,
            InterviewPlanningService interviewPlanningService,
            CareerInterviewExecutionBridge interviewExecutionBridge,
            HybridCompactingChatMemory chatMemory,
            ResumeStructuringService resumeStructuringService,
            ResumePdfTextExtractor resumePdfTextExtractor,
            ResumeRenderService resumeRenderService,
            @Qualifier("careerTaskExecutor") TaskExecutor careerTaskExecutor,
            ObjectProvider<AiTracePublisher> tracePublisherProvider,
            ResumeObjectStorage resumeObjectStorage,
            RagResumeAutoTagService ragResumeAutoTagService) {
        this.resumeStore = resumeStore;
        this.jobMatchTaskStore = jobMatchTaskStore;
        this.resumeParseTaskStore = resumeParseTaskStore;
        this.resumeRagService = resumeRagService;
        this.cvOptimizationOrchestrator = cvOptimizationOrchestrator;
        this.interviewPlanningService = interviewPlanningService;
        this.interviewExecutionBridge = interviewExecutionBridge;
        this.chatMemory = chatMemory;
        this.resumeStructuringService = resumeStructuringService;
        this.resumePdfTextExtractor = resumePdfTextExtractor;
        this.resumeRenderService = resumeRenderService;
        this.careerTaskExecutor = careerTaskExecutor;
        this.tracePublisherProvider = tracePublisherProvider;
        this.resumeObjectStorage = resumeObjectStorage == null ? ResumeObjectStorage.disabled() : resumeObjectStorage;
        this.ragResumeAutoTagService = ragResumeAutoTagService;
    }

    /** 保持既有单元测试和非 Spring 调用方的构造方式兼容，自动标签在该场景下不参与执行。 */
    public ResumeApplicationService(
            ResumeStore resumeStore, JobMatchTaskStore jobMatchTaskStore, ResumeParseTaskStore resumeParseTaskStore,
            ResumeRagService resumeRagService, CvOptimizationOrchestrator cvOptimizationOrchestrator,
            InterviewPlanningService interviewPlanningService, CareerInterviewExecutionBridge interviewExecutionBridge,
            HybridCompactingChatMemory chatMemory, ResumeStructuringService resumeStructuringService,
            ResumePdfTextExtractor resumePdfTextExtractor, ResumeRenderService resumeRenderService,
            TaskExecutor careerTaskExecutor, ObjectProvider<AiTracePublisher> tracePublisherProvider,
            ResumeObjectStorage resumeObjectStorage) {
        this(resumeStore, jobMatchTaskStore, resumeParseTaskStore, resumeRagService, cvOptimizationOrchestrator,
                interviewPlanningService, interviewExecutionBridge, chatMemory, resumeStructuringService,
                resumePdfTextExtractor, resumeRenderService, careerTaskExecutor, tracePublisherProvider,
                resumeObjectStorage, null);
    }

    public ResumeUploadResult upload(Long userId, MultipartFile file) {
        long start = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        try {
            CvBO parsed = parseUpload(userId, file);
            CvBO saved = resumeStore.save(parsed);
            if (ragResumeAutoTagService != null) ragResumeAutoTagService.generateAndReplace(saved);
            chatMemory.add(memoryId(saved.getId()), MemoryMessage.builder()
                    .role(MemoryRole.USER)
                    .content("Resume uploaded and parsed: " + saved.getName() + " / " + saved.getTitle())
                    .metadata(Map.of("scene", "RESUME_ANALYSIS", "resumeId", String.valueOf(saved.getId()), "userId", String.valueOf(userId)))
                    .build());
            ResumeEmbeddingResult embedding = embeddingOwned(userId, saved.getId(), false);
            publishTool(traceId, "resume:" + saved.getId(), "RESUME_ANALYSIS", "resume-upload", saved.getName(),
                    "embeddingStatus=" + embedding.status() + ", chunkCount=" + embedding.chunkCount(),
                    embedding.errorMessage() == null, start, embedding.errorMessage(), Map.of("userId", userId, "resumeId", saved.getId()));
            return new ResumeUploadResult(saved.getId(), saved, embedding.status(), embedding.chunkCount(), embedding.errorMessage());
        } catch (Exception ex) {
            publishTool(traceId, "resume:upload", "RESUME_ANALYSIS", "resume-upload", safeFilename(file), "FAILED", false, start, ex.getMessage(), Map.of("userId", userId));
            if (ex instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Resume upload failed: " + ex.getMessage(), ex);
        }
    }

    public ResumeEmbeddingResult embedding(Long userId, Long resumeId) {
        return embeddingOwned(userId, resumeId, true);
    }

    public JobMatchTaskResult matchResumes(Long userId, String jobDescription, List<Long> requestedResumeIds, int limit) {
        return matchResumes(userId, jobDescription, requestedResumeIds, limit, null);
    }

    public JobMatchTaskResult matchResumes(Long userId, String jobDescription, List<Long> requestedResumeIds, int limit, Boolean ragOverride) {
        validateTextLength(jobDescription, MAX_JD_LENGTH, "Job description");
        List<ResumeParseTaskRecord> selectedTasks = validateSelectedMatchResumes(userId, requestedResumeIds);
        List<Long> selectedResumeIds = selectedTasks.stream().map(ResumeParseTaskRecord::resumeId).toList();
        String taskId = UUID.randomUUID().toString();
        int boundedLimit = Math.min(limit <= 0 ? 3 : limit, 20);
        JobMatchTaskResult started = JobMatchTaskResult.started(taskId, userId, selectedResumeIds);
        jobMatchTaskStore.save(started, jobDescription, boundedLimit, null);
        long start = System.currentTimeMillis();
        String sessionId = "job-match:" + taskId;
        publishBusinessInvocationStarted(taskId, "JD_ALIGNMENT", sessionId, "岗位匹配 RAG", jobDescription);
        try {
            Map<Long, ResumeParseTaskRecord> tasksByResumeId = selectedTasks.stream()
                    .collect(java.util.stream.Collectors.toMap(ResumeParseTaskRecord::resumeId, task -> task, (left, right) -> left, LinkedHashMap::new));
            List<CvBO> selectedResumes = selectedResumeIds.stream().map(resumeId -> getResume(userId, resumeId)).toList();
            selectedResumes.forEach(this::ensureResumeEmbedding);
            Set<String> resumeIds = selectedResumeIds.stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            boolean ragEnabled = ragOverride == null ? resumeRagService.enabled() : ragOverride;
            List<ResumeRagMatch> ragMatches = resumeRagService.retrieveResumeMatches(
                    jobDescription, boundedLimit, userId, resumeIds, ragEnabled, taskId, "JD_ALIGNMENT");
            Map<Long, CvBO> resumesById = selectedResumes.stream()
                    .collect(java.util.stream.Collectors.toMap(CvBO::getId, cv -> cv));
            if (!ragEnabled) {
                ragMatches = selectedResumes.stream()
                        .map(cv -> new ResumeRagMatch(String.valueOf(cv.getId()),
                                matchedTokens(jobDescription, String.valueOf(cv)).size(), List.of()))
                        .sorted(java.util.Comparator.comparingDouble(ResumeRagMatch::score).reversed())
                        .limit(boundedLimit)
                        .toList();
            }
            List<JobMatchCandidate> candidates = toJobMatchCandidates(jobDescription, ragMatches, resumesById, tasksByResumeId);
            List<String> templates = ragMatches.stream().flatMap(match -> match.evidence().stream()).toList();
            JobMatchTaskResult completed = JobMatchTaskResult.completed(taskId, userId, selectedResumeIds, candidates, templates);
            publishTool(taskId, "job-match:" + taskId, "JD_ALIGNMENT", "resume-rag-match", jobDescription,
                    "matched=" + candidates.size(), true, start, null, Map.of("userId", userId, "resumeScope", resumeIds.size()));
            publishBusinessInvocationCompleted(taskId, "JD_ALIGNMENT", sessionId, "岗位匹配 RAG", start,
                    "matched=" + candidates.size(), Map.of(
                            "ragRequested", true,
                            "ragEnabled", ragEnabled,
                            "businessType", "JOB_MATCH",
                            "resumeScope", resumeIds.size(),
                            "resultCount", candidates.size()
                    ));
            return jobMatchTaskStore.save(completed, jobDescription, boundedLimit, null);
        } catch (Exception ex) {
            log.warn("岗位匹配任务执行失败。任务编号={}", taskId, ex);
            JobMatchTaskResult failed = JobMatchTaskResult.failed(taskId, userId, selectedResumeIds, ex.getMessage());
            publishTool(taskId, "job-match:" + taskId, "JD_ALIGNMENT", "resume-rag-match", jobDescription,
                    "FAILED", false, start, ex.getMessage(), Map.of("userId", userId));
            publishBusinessInvocationFailed(taskId, "JD_ALIGNMENT", sessionId, "岗位匹配 RAG", start, ex);
            return jobMatchTaskStore.save(failed, jobDescription, boundedLimit, ex.getMessage());
        }
    }

    public JobMatchTaskResult getMatchTask(Long userId, String taskId) {
        return jobMatchTaskStore.findByTaskIdAndUserId(taskId, userId).orElseGet(() -> JobMatchTaskResult.notFound(taskId));
    }

    public JobMatchHistoryPage listMatchHistory(Long userId, int limit) {
        int normalizedLimit = limit < 0 ? 20 : limit;
        return new JobMatchHistoryPage(
                jobMatchTaskStore.findRecentByUserId(userId, normalizedLimit),
                jobMatchTaskStore.countByUserId(userId));
    }

    public CvOptimizationResult optimize(Long userId, Long resumeId, String jobDescription) {
        return optimize(userId, resumeId, jobDescription, (Consumer<CvReview>) null, (Boolean) null);
    }

    public CvOptimizationResult optimize(
            Long userId,
            Long resumeId,
            String jobDescription,
            Consumer<CvReview> progressCallback) {
        return optimize(userId, resumeId, jobDescription, progressCallback, null);
    }

    public CvOptimizationResult optimizeWithRagOverride(Long userId, Long resumeId, String jobDescription, Boolean ragOverride) {
        return optimize(userId, resumeId, jobDescription, null, ragOverride);
    }

    public CvOptimizationResult optimizeWithRagOverride(
            Long userId, Long resumeId, String jobDescription, Boolean ragOverride, Consumer<CvReview> progressCallback) {
        return optimize(userId, resumeId, jobDescription, progressCallback, ragOverride);
    }

    private CvOptimizationResult optimize(
            Long userId, Long resumeId, String jobDescription, Consumer<CvReview> progressCallback, Boolean ragOverride) {
        long optimizationStart = System.currentTimeMillis();
        validateTextLength(jobDescription, MAX_JD_LENGTH, "Job description");
        CvBO cv = getResume(userId, resumeId);
        ensureResumeEmbedding(cv);
        boolean ragEnabled = ragOverride == null ? resumeRagService.enabled() : ragOverride;
        String optimizationTraceId = UUID.randomUUID().toString();
        String optimizationSessionId = "resume-optimization:" + resumeId;
        String agentRunId = startAgentRun(
                "RESUME_OPTIMIZATION",
                "RESUME_TAILOR",
                userId,
                String.valueOf(resumeId),
                optimizationSessionId,
                optimizationTraceId,
                ragEnabled,
                "JD长度=" + jobDescription.length(),
                "rag=" + ragEnabled + ";templateTopK=3"
        );
        publishBusinessInvocationStarted(optimizationTraceId, "RESUME_TAILOR", optimizationSessionId, "简历优化 RAG", jobDescription);
        List<String> templates;
        try {
            recordAgentRunStage(agentRunId, "RAG_RETRIEVE", "开始检索简历优化证据", Map.of("ragEnabled", ragEnabled));
            templates = resumeRagService.retrieveTemplates(
                    jobDescription, 3, userId, Set.of(String.valueOf(resumeId)), ragEnabled,
                    optimizationTraceId, "RESUME_TAILOR");
            recordAgentRunStage(agentRunId, "RAG_RETRIEVE", "简历优化证据检索完成", Map.of("templateCount", templates.size()));
            publishBusinessInvocationCompleted(optimizationTraceId, "RESUME_TAILOR", optimizationSessionId,
                    "简历优化 RAG", optimizationStart, "templates=" + templates.size(), Map.of(
                            "ragRequested", true,
                            "ragEnabled", ragEnabled,
                            "businessType", "RESUME_OPTIMIZATION",
                            "resumeId", resumeId,
                            "resultCount", templates.size()
                    ));
        } catch (RuntimeException ex) {
            failAgentRun(agentRunId, "RAG 检索失败：" + ex.getMessage(), Map.of("stage", "RAG_RETRIEVE"));
            publishBusinessInvocationFailed(optimizationTraceId, "RESUME_TAILOR", optimizationSessionId,
                    "简历优化 RAG", optimizationStart, ex);
            throw ex;
        }
        String resumeMemoryId = memoryId(resumeId);
        final int[] iterationCounter = {0};
        Consumer<CvReview> memoryAwareProgressCallback = review -> {
            iterationCounter[0]++;
            chatMemory.add(resumeMemoryId, MemoryMessage.builder()
                    .role(MemoryRole.ASSISTANT)
                    .content("Resume optimization iteration " + iterationCounter[0]
                            + ": score=" + review.score()
                            + ", feedback=" + review.feedback())
                    .metadata(Map.of(
                            "scene", "RESUME_TAILOR",
                            "resumeId", String.valueOf(resumeId),
                            "userId", String.valueOf(userId),
                            "iteration", String.valueOf(iterationCounter[0])
                    ))
                    .build());
            if (progressCallback != null) {
                progressCallback.accept(review);
            }
        };
        CvOptimizationResult result;
        try {
            recordAgentRunStage(agentRunId, "AGENT_OPTIMIZE", "开始执行简历优化 Agent", Map.of());
            result = cvOptimizationOrchestrator.optimize(cv, jobDescription, templates, memoryAwareProgressCallback);
            recordAgentRunStage(agentRunId, "AGENT_OPTIMIZE", "简历优化 Agent 执行完成", Map.of("iterations", result.iterations()));
        } catch (RuntimeException ex) {
            failAgentRun(agentRunId, "简历优化 Agent 执行失败：" + ex.getMessage(), Map.of("stage", "AGENT_OPTIMIZE"));
            throw ex;
        }
        CvBO latest = result.cv() == null ? cv : result.cv().toBuilder().id(resumeId).userId(userId).build();
        if (result.scoreGatePassed()) {
            resumeStore.save(latest);
            recordAgentRunStage(agentRunId, "PERSIST", "优化后的简历已持久化", Map.of("scoreGatePassed", true));
            embeddedResumeIds.remove(resumeId);
            ensureResumeEmbedding(latest);
        }
        chatMemory.add(resumeMemoryId, MemoryMessage.builder()
                .role(MemoryRole.ASSISTANT)
                .content("Resume optimization decision: scoreGatePassed=" + result.scoreGatePassed()
                        + ", iterations=" + result.iterations()
                        + ", bestReview=" + result.bestReview())
                .metadata(Map.of("scene", "RESUME_TAILOR", "resumeId", String.valueOf(resumeId), "userId", String.valueOf(userId)))
                .build());
        CvOptimizationResult response = CvOptimizationResult.builder()
                .cv(latest)
                .bestReview(result.bestReview())
                .iterations(result.iterations())
                .scoreGatePassed(result.scoreGatePassed())
                .failureReason(result.failureReason())
                .reviewHistory(result.reviewHistory())
                .build();
        resumeParseTaskStore.findByUserId(userId, ResumeParseTaskStatus.COMPLETED.name()).stream()
                .filter(task -> resumeId.equals(task.resumeId()))
                .findFirst()
                .ifPresent(task -> resumeParseTaskStore.save(task.withOptimization(jobDescription, JSON.toJSONString(response))));
        long durationMs = System.currentTimeMillis() - optimizationStart;
        log.info("性能指标 指标名称=简历优化完成 RAG开关={} 用户编号={} 简历编号={} 总耗时毫秒={} 迭代次数={} 评分达标={}",
                ragEnabled, userId, resumeId, durationMs, response.iterations(), response.scoreGatePassed());
        publishTool(UUID.randomUUID().toString(), memoryId(resumeId), "RESUME_TAILOR", "resume-optimization-total",
                jobDescription, "completed", true, optimizationStart, null,
                Map.of("指标名称", "简历优化完成", "RAG开关", ragEnabled, "RAG来源", ragOverride == null ? "后端默认配置" : "前端单次选择", "用户编号", userId, "简历编号", resumeId,
                        "总耗时毫秒", durationMs, "迭代次数", response.iterations(),
                        "评分达标", Boolean.TRUE.equals(response.scoreGatePassed())));
        succeedAgentRun(agentRunId, "简历优化完成", Map.of(
                "iterations", response.iterations(),
                "scoreGatePassed", Boolean.TRUE.equals(response.scoreGatePassed()),
                "ragEnabled", ragEnabled
        ));
        return response;
    }

    /** 记录运行总账失败时只告警，确保 Harness 的观测能力不会反向影响用户的简历优化结果。 */
    private String startAgentRun(
            String businessType, String sceneCode, Long userId, String businessId, String sessionId, String traceId,
            boolean ragEnabled, String inputSummary, String configFingerprint) {
        if (agentRunCoordinator == null) {
            return null;
        }
        try {
            return agentRunCoordinator.start(new AgentRunStartCommand(
                    businessType, sceneCode, userId, businessId, sessionId, traceId,
                    ragEnabled, inputSummary, configFingerprint));
        } catch (RuntimeException ex) {
            log.warn("Harness 运行总账创建失败，已跳过审计但继续简历优化。traceId={}", traceId, ex);
            return null;
        }
    }

    private void recordAgentRunStage(String runId, String stageCode, String message, Map<String, Object> metadata) {
        if (runId == null || agentRunCoordinator == null) {
            return;
        }
        try {
            agentRunCoordinator.stage(runId, stageCode, message, metadata);
        } catch (RuntimeException ex) {
            log.warn("Harness 阶段事件写入失败，已跳过。runId={}, stage={}", runId, stageCode, ex);
        }
    }

    private void succeedAgentRun(String runId, String summary, Map<String, Object> metadata) {
        if (runId == null || agentRunCoordinator == null) {
            return;
        }
        try {
            agentRunCoordinator.succeed(runId, summary, metadata);
        } catch (RuntimeException ex) {
            log.warn("Harness 成功终态写入失败，已跳过。runId={}", runId, ex);
        }
    }

    private void failAgentRun(String runId, String message, Map<String, Object> metadata) {
        if (runId == null || agentRunCoordinator == null) {
            return;
        }
        try {
            agentRunCoordinator.fail(runId, message, metadata);
        } catch (RuntimeException ignored) {
            // 失败路径不能因二次审计失败掩盖原始业务异常。
        }
    }

    public List<ResumeOptimizationHistoryResult> listOptimizationHistory(Long userId) {
        return resumeParseTaskStore.findByUserId(userId, ResumeParseTaskStatus.COMPLETED.name()).stream()
                .filter(task -> task.optimizationResultJson() != null && !task.optimizationResultJson().isBlank())
                .map(task -> toOptimizationHistoryResult(task))
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(ResumeOptimizationHistoryResult::optimizedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .limit(20)
                .toList();
    }

    private ResumeOptimizationHistoryResult toOptimizationHistoryResult(ResumeParseTaskRecord task) {
        try {
            return new ResumeOptimizationHistoryResult(task.taskId(), task.resumeId(), task.originalFilename(),
                    task.jobDescription(), JSON.parseObject(task.optimizationResultJson()), task.optimizedAt());
        } catch (Exception ex) {
            log.warn("已跳过格式异常的简历优化历史记录。任务编号={}，用户编号={}", task.taskId(), task.userId(), ex);
            return null;
        }
    }

    public InterviewPlan planInterview(Long userId, String sessionId, Long resumeId, String jobDescription) {
        validateTextLength(jobDescription, MAX_JD_LENGTH, "Job description");
        String memoryId = memoryId(userId, sessionId, resumeId);
        InterviewPlan plan = interviewPlanningService.plan(memoryId, sessionId, getResume(userId, resumeId), jobDescription);
        interviewExecutionBridge.publishPlan(userId, sessionId, plan);
        chatMemory.add(memoryId, MemoryMessage.builder()
                .role(MemoryRole.ASSISTANT)
                .content("Interview plan generated. firstQuestion=" + plan.firstQuestion() + ", alignment=" + plan.alignment())
                .metadata(Map.of("scene", "INTERVIEW_COORDINATION", "resumeId", String.valueOf(resumeId), "userId", String.valueOf(userId)))
                .build());
        return plan;
    }

    public ReflectionResult reflect(Long userId, String sessionId, Long resumeId, String currentQuestion, String userAnswer) {
        validateTextLength(currentQuestion, MAX_QUESTION_LENGTH, "Current question");
        validateTextLength(userAnswer, MAX_ANSWER_LENGTH, "User answer");
        CvBO cv = getResume(userId, resumeId);
        String memoryId = memoryId(userId, sessionId, resumeId);
        chatMemory.add(memoryId, MemoryMessage.builder()
                .role(MemoryRole.USER)
                .content("Question: " + currentQuestion + "\nAnswer: " + userAnswer)
                .metadata(Map.of("scene", "INTERVIEW_REFLECTION", "resumeId", String.valueOf(resumeId), "userId", String.valueOf(userId)))
                .build());
        ReflectionResult result = interviewPlanningService.reflect(memoryId, currentQuestion, userAnswer, cv);
        chatMemory.add(memoryId, MemoryMessage.builder()
                .role(MemoryRole.ASSISTANT)
                .content("Reflect decision: " + result.decision() + ", score=" + result.score() + ", feedback=" + result.feedback())
                .metadata(Map.of("scene", "INTERVIEW_REFLECTION", "resumeId", String.valueOf(resumeId), "userId", String.valueOf(userId)))
                .build());
        return result;
    }

    public ResumeParseTaskResult uploadAsync(Long userId, MultipartFile file, String cvType) {
        validateUpload(file);
        String taskId = UUID.randomUUID().toString();
        byte[] snapshot = readFileSnapshot(file);
        ResumeParseTaskRecord duplicate = findDuplicateUpload(userId, sha256(snapshot));
        if (duplicate != null) {
            log.info("简历上传复用了已有解析任务，任务编号={}，任务状态={}，用户编号={}",
                    duplicate.taskId(), duplicate.status(), userId);
            return toParseTaskResult(duplicate);
        }
        ResumeObjectStorageResult storage = storeAsyncUpload(taskId, file, snapshot);
        Instant now = Instant.now();
        ResumeParseTaskRecord task = new ResumeParseTaskRecord(
                taskId,
                userId,
                ResumeParseTaskStatus.PROCESSING,
                null,
                safeFilename(file),
                file.getSize(),
                file.getContentType(),
                StringUtils.hasText(cvType) ? cvType : "upload",
                storage == null ? "local-snapshot" : storage.provider(),
                storage == null ? taskId : storage.key(),
                storage == null ? null : storage.path(),
                storage == null ? snapshot : new byte[0],
                null,
                null,
                now,
                null,
                now,
                now
        );
        resumeParseTaskStore.save(task);
        log.info("简历上传已受理，进入异步解析队列。任务编号={}，用户编号={}，文件名={}，文件大小字节={}",
                taskId, userId, task.originalFilename(), task.fileSize());
        careerTaskExecutor.execute(() -> processParseTask(taskId));
        return toParseTaskResult(task);
    }

    public ResumeParseTaskResult getParseTask(Long userId, String taskId) {
        return resumeParseTaskStore.findByTaskIdAndUserId(taskId, userId)
                .map(this::toParseTaskResult)
                .orElseGet(() -> ResumeParseTaskResult.notFound(taskId));
    }

    public List<ResumeParseTaskResult> listParseTasks(Long userId, String status) {
        List<ResumeParseTaskRecord> tasks = resumeParseTaskStore.findByUserId(userId, status);
        if (ResumeParseTaskStatus.COMPLETED.name().equalsIgnoreCase(status)) {
            tasks = hideDuplicateCompletedTasks(tasks);
        }
        return tasks.stream()
                .map(this::toParseTaskResult)
                .toList();
    }

    public void updateMatchResumeOrder(Long userId, List<Long> resumeIds) {
        if (resumeIds == null || resumeIds.isEmpty() || new LinkedHashSet<>(resumeIds).size() != resumeIds.size()) {
            throw new IllegalArgumentException("简历排序必须包含唯一的简历编号");
        }
        List<ResumeParseTaskRecord> completed = resumeParseTaskStore.findByUserId(userId, ResumeParseTaskStatus.COMPLETED.name());
        Set<Long> ownedResumeIds = completed.stream().map(ResumeParseTaskRecord::resumeId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (!ownedResumeIds.containsAll(resumeIds)) {
            throw new IllegalArgumentException("简历排序中包含不可用或不属于当前用户的简历");
        }
        resumeParseTaskStore.updateDisplayOrder(userId, resumeIds);
    }

    public ResumeParseTaskResult cancelParseTask(Long userId, String taskId) {
        ResumeParseTaskRecord task = resumeParseTaskStore.findByTaskIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Resume parse task not found or not owned by current user: " + taskId));
        if (task.status().terminal()) {
            return toParseTaskResult(task);
        }
        ResumeParseTaskRecord canceled = task.withStatus(ResumeParseTaskStatus.CANCELED);
        resumeParseTaskStore.save(canceled);
        return toParseTaskResult(canceled);
    }

    public ResumeParseTaskResult retryParseTask(Long userId, String taskId) {
        ResumeParseTaskRecord source = resumeParseTaskStore.findByTaskIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Resume parse task not found or not owned by current user: " + taskId));
        if (source.status() != ResumeParseTaskStatus.FAILED) {
            throw new IllegalArgumentException("Only FAILED resume parse tasks can be retried: " + taskId);
        }
        ResumeParseTaskRecord retry = source.retry(UUID.randomUUID().toString());
        resumeParseTaskStore.save(retry);
        careerTaskExecutor.execute(() -> processParseTask(retry.taskId()));
        return toParseTaskResult(retry);
    }

    public int recoverStaleParseTasks(Duration staleAfter, int limit) {
        Duration threshold = staleAfter == null || staleAfter.isNegative() || staleAfter.isZero()
                ? Duration.ofMinutes(30)
                : staleAfter;
        int boundedLimit = Math.min(Math.max(limit, 1), 100);
        Instant updatedBefore = Instant.now().minus(threshold);
        List<ResumeParseTaskRecord> staleTasks = resumeParseTaskStore.findStaleActiveTasks(updatedBefore, boundedLimit);
        int recovered = 0;
        for (ResumeParseTaskRecord task : staleTasks) {
            if (task == null || task.status() == null || task.status().terminal()) {
                continue;
            }
            recovered++;
            if (!hasRecoverablePayload(task)) {
                resumeParseTaskStore.save(task.failed("Resume parse task recovery failed: missing recoverable file payload"));
                continue;
            }
            ResumeParseTaskRecord queued = task.withStatus(task.status());
            resumeParseTaskStore.save(queued);
            careerTaskExecutor.execute(() -> processParseTask(queued.taskId()));
        }
        return recovered;
    }

    public ResumeRenderArtifact renderResume(Long userId, Long resumeId, String format) {
        CvBO cv = getResume(userId, resumeId);
        String normalized = format == null ? "" : format.trim().toLowerCase();
        return switch (normalized) {
            case "md", "markdown" -> resumeRenderService.renderMarkdown(cv);
            case "html" -> resumeRenderService.renderHtml(cv);
            case "pdf" -> resumeRenderService.renderPdf(cv);
            case "docx", "word" -> resumeRenderService.renderDocx(cv);
            default -> throw new IllegalArgumentException("Unsupported resume render format: " + format
                    + ". Supported: markdown, html, pdf, docx");
        };
    }

    public ResumeRenderStorageResult renderResumeAndStore(Long userId, Long resumeId, String format) {
        if (!resumeObjectStorage.enabled()) {
            throw new IllegalStateException("Resume object storage is disabled");
        }
        ResumeRenderArtifact artifact = renderResume(userId, resumeId, format);
        ResumeObjectStorageResult storage = resumeObjectStorage.put(
                renderObjectStorageKey(userId, resumeId, artifact),
                artifact.bytes(),
                artifact.contentType(),
                artifact.filename()
        );
        return new ResumeRenderStorageResult(artifact, storage);
    }


    private ResumeEmbeddingResult embeddingOwned(Long userId, Long resumeId, boolean failOnError) {
        CvBO cv = getResume(userId, resumeId);
        return doEmbedding(cv, failOnError);
    }

    private void processParseTask(String taskId) {
        ResumeParseTaskRecord task = resumeParseTaskStore.findByTaskId(taskId).orElse(null);
        if (task == null || task.status() == ResumeParseTaskStatus.CANCELED) {
            return;
        }
        long parseStart = System.currentTimeMillis();
        try {
            if (task.status() == ResumeParseTaskStatus.SAVING && task.resumeId() != null) {
                CvBO existing = resumeStore.findByIdAndUserId(task.resumeId(), task.userId())
                        .orElseThrow(() -> new IllegalStateException("Resume parse recovery failed: saved resume not found"));
                doEmbedding(existing, false);
                resumeParseTaskStore.save(task.completed(existing.getId()));
                publishParseMetric(task, existing.getId(), parseStart, true, null);
                return;
            }
            task = task.withStatus(ResumeParseTaskStatus.ANALYZING);
            resumeParseTaskStore.save(task);
            CvBO parsed = parseUpload(task.userId(), multipartFromTask(task));
            if (isCanceled(taskId, task.userId())) {
                return;
            }
            task = task.withStatus(ResumeParseTaskStatus.SAVING);
            resumeParseTaskStore.save(task);
            CvBO saved = resumeStore.save(parsed.toBuilder()
                    .userId(task.userId())
                    .cvType(StringUtils.hasText(task.cvType()) ? task.cvType() : "upload")
                    .build());
            if (ragResumeAutoTagService != null) ragResumeAutoTagService.generateAndReplace(saved);
            doEmbedding(saved, false);
            resumeParseTaskStore.save(task.completed(saved.getId()));
            publishParseMetric(task, saved.getId(), parseStart, true, null);
        } catch (Exception ex) {
            ResumeParseTaskRecord latest = resumeParseTaskStore.findByTaskId(taskId).orElse(task);
            if (latest != null && latest.status() == ResumeParseTaskStatus.CANCELED) {
                return;
            }
            ResumeParseTaskRecord failed = (latest == null ? task : latest).failed(ex.getMessage());
            resumeParseTaskStore.save(failed);
            publishParseMetric(task, task.resumeId(), parseStart, false, ex.getMessage());
            log.warn("简历解析任务失败。任务编号={}，用户编号={}", taskId, task.userId(), ex);
        }
    }

    private boolean isCanceled(String taskId, Long userId) {
        return resumeParseTaskStore.findByTaskIdAndUserId(taskId, userId)
                .map(task -> task.status() == ResumeParseTaskStatus.CANCELED)
                .orElse(true);
    }

    private void publishParseMetric(ResumeParseTaskRecord task, Long resumeId, long startMillis, boolean success, String errorMessage) {
        long durationMs = Math.max(0, System.currentTimeMillis() - startMillis);
        log.info("性能指标 指标名称=PDF解析完成 任务编号={} 用户编号={} 简历编号={} 解析耗时毫秒={} 成功={} 文件大小字节={}",
                task.taskId(), task.userId(), resumeId, durationMs, success, task.fileSize());
        publishTool(task.taskId(), "resume-parse:" + task.taskId(), "RESUME_ANALYSIS", "resume-pdf-parse",
                task.originalFilename(), success ? "completed" : "failed", success, startMillis, errorMessage,
                Map.of("指标名称", "PDF解析完成", "任务编号", task.taskId(), "用户编号", task.userId(),
                        "简历编号", String.valueOf(resumeId), "文件大小字节", task.fileSize() == null ? 0L : task.fileSize(), "解析耗时毫秒", durationMs));
    }

    /**
     * 在同一用户范围内按文件内容哈希查找重复上传，而非按文件名判断。
     *
     * <p>用户可重命名同一份 PDF，因此只有内容哈希一致才视为重复；已失败或主动取消的任务
     * 不会阻塞用户重新上传。</p>
     */
    private ResumeParseTaskRecord findDuplicateUpload(Long userId, String fileHash) {
        if (!StringUtils.hasText(fileHash)) {
            return null;
        }
        return resumeParseTaskStore.findByUserId(userId, null).stream()
                .filter(task -> task.status() != ResumeParseTaskStatus.FAILED
                        && task.status() != ResumeParseTaskStatus.CANCELED)
                .filter(task -> fileHash.equals(taskFileHash(task)))
                .findFirst()
                .orElse(null);
    }

    /**
     * 仅在候选列表中隐藏内容完全相同的历史简历。
     *
     * <p>底层记录不会被删除，确保历史追溯与任务审计仍然完整；同时严格保留存储层已确定的
     * 用户自定义展示顺序，避免去重导致拖拽排序在刷新后被重排。</p>
     */
    private List<ResumeParseTaskRecord> hideDuplicateCompletedTasks(List<ResumeParseTaskRecord> tasks) {
        Map<String, ResumeParseTaskRecord> unique = new LinkedHashMap<>();
        // 存储层已应用用户自定义展示顺序；去重只能移除重复文件，绝不能改变候选列表顺序。
        tasks.forEach(task -> unique.putIfAbsent(taskFileHash(task), task));
        return List.copyOf(unique.values());
    }

    private String taskFileHash(ResumeParseTaskRecord task) {
        if (task == null) {
            return "";
        }
        byte[] snapshot = task.fileSnapshot();
        if (snapshot.length > 0) {
            return sha256(snapshot);
        }
        if (resumeObjectStorage.enabled() && StringUtils.hasText(task.storageKey())) {
            try (InputStream input = resumeObjectStorage.get(task.storageKey())) {
                return input == null ? "task:" + task.taskId() : sha256(input.readAllBytes());
            } catch (Exception ex) {
                log.debug("无法计算简历文件去重哈希。任务编号={}", task.taskId(), ex);
            }
        }
        return "task:" + task.taskId();
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content == null ? new byte[0] : content);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private ResumeEmbeddingResult doEmbedding(CvBO cv, boolean failOnError) {
        long start = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        try {
            List<ResumeChunk> chunks = resumeRagService.storeCvBO(cv);
            embeddedResumeIds.put(cv.getId(), true);
            chatMemory.add(memoryId(cv.getId()), MemoryMessage.builder()
                    .role(MemoryRole.TOOL)
                    .content("Resume embedding completed. chunkCount=" + chunks.size())
                    .metadata(Map.of("scene", "RESUME_ANALYSIS", "resumeId", String.valueOf(cv.getId()), "userId", String.valueOf(cv.getUserId())))
                    .build());
            publishTool(traceId, memoryId(cv.getId()), "RESUME_ANALYSIS", "resume-embedding", cv.getName(),
                    "chunkCount=" + chunks.size(), true, start, null, Map.of("resumeId", cv.getId(), "userId", cv.getUserId()));
            return ResumeEmbeddingResult.completed(cv.getId(), chunks);
        } catch (Exception ex) {
            log.warn("简历向量化失败。简历编号={}", cv.getId(), ex);
            publishTool(traceId, memoryId(cv.getId()), "RESUME_ANALYSIS", "resume-embedding", cv.getName(),
                    "FAILED", false, start, ex.getMessage(), Map.of("resumeId", cv.getId(), "userId", cv.getUserId()));
            if (failOnError) {
                throw new IllegalStateException("Resume embedding failed: " + ex.getMessage(), ex);
            }
            return ResumeEmbeddingResult.failed(cv.getId(), ex.getMessage());
        }
    }

    private List<ResumeParseTaskRecord> validateSelectedMatchResumes(Long userId, List<Long> requestedResumeIds) {
        if (requestedResumeIds == null || requestedResumeIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one completed resume before matching");
        }
        if (requestedResumeIds.size() > 20 || new LinkedHashSet<>(requestedResumeIds).size() != requestedResumeIds.size()) {
            throw new IllegalArgumentException("Selected resume IDs must be unique and cannot exceed 20");
        }
        Map<Long, ResumeParseTaskRecord> completed = resumeParseTaskStore
                .findByUserId(userId, ResumeParseTaskStatus.COMPLETED.name()).stream()
                .filter(task -> task.resumeId() != null)
                .collect(java.util.stream.Collectors.toMap(ResumeParseTaskRecord::resumeId, task -> task, (left, right) -> left));
        List<ResumeParseTaskRecord> selected = new ArrayList<>();
        for (Long resumeId : requestedResumeIds) {
            ResumeParseTaskRecord task = resumeId == null ? null : completed.get(resumeId);
            if (task == null) {
                throw new IllegalArgumentException("Selected resume is unavailable, unfinished, or not owned by the current user: " + resumeId);
            }
            selected.add(task);
        }
        return selected;
    }

    private List<JobMatchCandidate> toJobMatchCandidates(
            String jobDescription,
            List<ResumeRagMatch> ragMatches,
            Map<Long, CvBO> resumesById,
            Map<Long, ResumeParseTaskRecord> tasksByResumeId) {
        double maxScore = ragMatches.stream().mapToDouble(ResumeRagMatch::score).max().orElse(0.0);
        List<JobMatchCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < ragMatches.size(); index++) {
            ResumeRagMatch match = ragMatches.get(index);
            Long resumeId;
            try {
                resumeId = Long.valueOf(match.resumeId());
            } catch (NumberFormatException ex) {
                continue;
            }
            CvBO resume = resumesById.get(resumeId);
            if (resume == null) {
                continue;
            }
            List<String> matchedPoints = matchedTokens(jobDescription, String.valueOf(resume));
            List<String> missingPoints = missingTokens(jobDescription, String.valueOf(resume));
            int rank = index + 1;
            int score = maxScore <= 0.0 ? 0 : (int) Math.round(Math.min(100.0, 100.0 * match.score() / maxScore));
            ResumeParseTaskRecord task = tasksByResumeId.get(resumeId);
            candidates.add(new JobMatchCandidate(
                    resumeId,
                    task == null ? resume.getName() : task.originalFilename(),
                    rank,
                    score,
                    rank == 1 ? "最推荐" : rank == 2 ? "推荐" : "可备选",
                    matchedPoints.isEmpty() ? match.evidence() : matchedPoints,
                    missingPoints));
        }
        return candidates;
    }

    private List<String> matchedTokens(String jobDescription, String resumeText) {
        String normalizedResume = resumeText == null ? "" : resumeText.toLowerCase();
        return jobTokens(jobDescription).stream().filter(normalizedResume::contains).limit(8).toList();
    }

    private List<String> missingTokens(String jobDescription, String resumeText) {
        String normalizedResume = resumeText == null ? "" : resumeText.toLowerCase();
        return jobTokens(jobDescription).stream().filter(token -> !normalizedResume.contains(token)).limit(6).toList();
    }

    private List<String> jobTokens(String jobDescription) {
        return java.util.Arrays.stream((jobDescription == null ? "" : jobDescription).toLowerCase().split("[^\\p{IsHan}\\p{Alnum}#+.]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .distinct()
                .limit(32)
                .toList();
    }

    private Set<String> ensureUserResumeEmbeddings(Long userId) {
        List<CvBO> resumes = resumeStore.findByUserId(userId);
        resumes.forEach(this::ensureResumeEmbedding);
        return resumes.stream()
                .map(CvBO::getId)
                .filter(id -> id != null)
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.toSet());
    }

    private void ensureResumeEmbedding(CvBO cv) {
        if (cv == null || cv.getId() == null || Boolean.TRUE.equals(embeddedResumeIds.get(cv.getId()))) {
            return;
        }
        doEmbedding(cv, false);
    }

    private CvBO getResume(Long userId, Long resumeId) {
        return resumeStore.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found or not owned by current user: " + resumeId));
    }

    private CvBO parseUpload(Long userId, MultipartFile file) {
        validateUpload(file);
        String originalFilename = file.getOriginalFilename();
        String content = extractText(file);
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("Resume parse failed: no text content extracted");
        }
        String summary = limitResumeText(content.trim());
        CvBO structured = resumeStructuringService == null ? null : resumeStructuringService.structure(userId, originalFilename, summary);
        if (structured != null) {
            return structured.toBuilder()
                    .userId(userId)
                    .cvType(StringUtils.hasText(structured.getCvType()) ? structured.getCvType() : "upload")
                    .name(StringUtils.hasText(structured.getName()) ? structured.getName() : (originalFilename == null ? "uploaded-resume" : originalFilename))
                    .summary(StringUtils.hasText(structured.getSummary()) ? structured.getSummary() : summary)
                    .build();
        }
        return CvBO.builder()
                .userId(userId)
                .cvType("upload")
                .name(originalFilename == null ? "uploaded-resume" : originalFilename)
                .title(inferTitle(summary))
                .summary(summary)
                .skills(inferSkills(summary))
                .build();
    }

    private MultipartFile multipartFromTask(ResumeParseTaskRecord task) {
        byte[] bytes = task.fileSnapshot();
        if (bytes.length == 0 && resumeObjectStorage.enabled() && StringUtils.hasText(task.storageKey())) {
            bytes = readObjectStorageSnapshot(task.storageKey());
        }
        return new SnapshotMultipartFile(task.originalFilename(), task.contentType(), bytes);
    }

    private boolean hasRecoverablePayload(ResumeParseTaskRecord task) {
        if (task.status() == ResumeParseTaskStatus.SAVING && task.resumeId() != null) {
            return true;
        }
        return task.fileSnapshot().length > 0 || (resumeObjectStorage.enabled() && StringUtils.hasText(task.storageKey()));
    }

    private byte[] readFileSnapshot(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Resume file snapshot failed: " + ex.getMessage(), ex);
        }
    }

    private ResumeObjectStorageResult storeAsyncUpload(String taskId, MultipartFile file, byte[] snapshot) {
        if (!resumeObjectStorage.enabled()) {
            return null;
        }
        try {
            return resumeObjectStorage.put(objectStorageKey(taskId, safeFilename(file)), snapshot, file.getContentType(), safeFilename(file));
        } catch (Exception ex) {
            log.warn("简历对象存储转交失败，已使用本地快照兜底。任务编号={}", taskId, ex);
            return null;
        }
    }

    private byte[] readObjectStorageSnapshot(String key) {
        try (InputStream input = resumeObjectStorage.get(key)) {
            return input.readAllBytes();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Resume object storage read failed: " + ex.getMessage(), ex);
        }
    }

    private String objectStorageKey(String taskId, String originalFilename) {
        return "career/resume/parse-task/" + taskId + "/" + sanitizeObjectName(originalFilename);
    }

    private String renderObjectStorageKey(Long userId, Long resumeId, ResumeRenderArtifact artifact) {
        String owner = userId == null ? "anonymous" : String.valueOf(userId);
        String id = resumeId == null ? "unknown" : String.valueOf(resumeId);
        String filename = artifact == null ? "resume" : sanitizeObjectName(artifact.filename());
        return "career/resume/render/" + owner + "/" + id + "/" + UUID.randomUUID() + "/" + filename;
    }

    private String sanitizeObjectName(String originalFilename) {
        String value = StringUtils.hasText(originalFilename) ? originalFilename : "uploaded-resume";
        return value.replace('\\', '_').replace('/', '_').replaceAll("[\\r\\n\\t]", "_");
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Resume file is empty");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("Resume file exceeds 5MB upload limit");
        }
        String filename = safeFilename(file);
        String ext = extension(filename);
        if (!(TEXT_EXTENSIONS.contains(ext) || "docx".equals(ext) || "pdf".equals(ext))) {
            throw new IllegalArgumentException("Unsupported resume file type: " + ext + ". Supported: txt, md, json, csv, docx, pdf");
        }
    }

    private String extractText(MultipartFile file) {
        try {
            String ext = extension(safeFilename(file));
            if ("pdf".equals(ext)) {
                try (InputStream input = file.getInputStream()) {
                    return resumePdfTextExtractor.extract(input);
                }
            }
            byte[] bytes = file.getBytes();
            if ("docx".equals(ext)) {
                return extractDocxText(bytes);
            }
            return decodeUtf8(bytes);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Resume parse failed: " + ex.getMessage(), ex);
        }
    }

    private String extractDocxText(byte[] bytes) throws Exception {
        File tempFile = File.createTempFile("xunzhi-resume-", ".docx");
        try {
            try (FileOutputStream output = new FileOutputStream(tempFile)) {
                output.write(bytes);
            }
            try (ZipFile zip = new ZipFile(tempFile)) {
                validateDocxZip(zip);
                ZipEntry documentXml = zip.getEntry("word/document.xml");
                if (documentXml == null) {
                    return "";
                }
                try (InputStream input = zip.getInputStream(documentXml)) {
                    String xml = new String(readLimitedEntry(input, MAX_DOCX_ENTRY_BYTES), StandardCharsets.UTF_8);
                    String normalized = xml.replace("</w:p>", "\n").replace("</w:tr>", "\n");
                    return limitResumeText(XML_TAG.matcher(normalized).replaceAll(" ").replaceAll("\\s+", " ").trim());
                }
            }
        } finally {
            if (!tempFile.delete()) {
                log.debug("临时 DOCX 文件暂未清理，将由系统后续回收。路径={}", tempFile.getAbsolutePath());
            }
        }
    }

    private void validateDocxZip(ZipFile zip) {
        long totalSize = 0L;
        int entries = 0;
        java.util.Enumeration<? extends ZipEntry> enumeration = zip.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            entries++;
            if (entries > MAX_DOCX_ENTRIES) {
                throw new IllegalArgumentException("DOCX contains too many entries");
            }
            long size = entry.getSize();
            if (!entry.isDirectory()) {
                if (size < 0) {
                    throw new IllegalArgumentException("DOCX entry has unknown uncompressed size: " + entry.getName());
                }
                if (size > MAX_DOCX_ENTRY_BYTES) {
                    throw new IllegalArgumentException("DOCX entry is too large: " + entry.getName());
                }
                totalSize += size;
                if (totalSize > MAX_DOCX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw new IllegalArgumentException("DOCX total uncompressed size exceeds limit");
                }
            }
        }
    }

    private byte[] readLimitedEntry(InputStream input, int maxBytes) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("DOCX document.xml exceeds " + maxBytes + " bytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String decodeUtf8(byte[] bytes) throws Exception {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return limitResumeText(decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString());
    }


    private String limitResumeText(String content) {
        if (content == null || content.length() <= MAX_RESUME_TEXT_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_RESUME_TEXT_LENGTH);
    }

    private void validateTextLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " length exceeds " + maxLength + " characters");
        }
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

    /**
     * 创建岗位匹配或简历优化的主业务调用记录。RAG 阶段会复用相同 traceId，
     * 因此监测页面可以从一次业务调用直接展开其 HyDE、召回、融合和重排链路。
     */
    private void publishBusinessInvocationStarted(
            String traceId, String sceneCode, String sessionId, String operationName, String input) {
        AiTracePublisher tracePublisher = tracePublisherProvider.getIfAvailable();
        if (tracePublisher != null) {
            tracePublisher.started(traceId, sceneCode, sessionId, "career-rag", operationName, input);
        }
    }

    private void publishBusinessInvocationCompleted(
            String traceId,
            String sceneCode,
            String sessionId,
            String operationName,
            long startMillis,
            String output,
            Map<String, Object> metadata) {
        AiTracePublisher tracePublisher = tracePublisherProvider.getIfAvailable();
        if (tracePublisher != null) {
            tracePublisher.completed(traceId, sceneCode, sessionId, "career-rag", operationName,
                    startMillis, output, metadata);
        }
    }

    private void publishBusinessInvocationFailed(
            String traceId, String sceneCode, String sessionId, String operationName, long startMillis, Throwable error) {
        AiTracePublisher tracePublisher = tracePublisherProvider.getIfAvailable();
        if (tracePublisher != null) {
            tracePublisher.failed(traceId, sceneCode, sessionId, "career-rag", operationName, startMillis, error);
        }
    }

    private void publishTool(
            String traceId,
            String sessionId,
            String sceneCode,
            String toolName,
            String input,
            String output,
            boolean success,
            long startMillis,
            String errorMessage,
            Map<String, Object> metadata) {
        AiTracePublisher tracePublisher = tracePublisherProvider.getIfAvailable();
        if (tracePublisher == null) {
            return;
        }
        tracePublisher.tool(new AiToolExecutionEvent(
                traceId,
                sessionId,
                null,
                sceneCode,
                toolName,
                abbreviate(input, 1000),
                abbreviate(output, 2000),
                success,
                Math.max(0, System.currentTimeMillis() - startMillis),
                0,
                errorMessage,
                Instant.now(),
                metadata == null ? Map.of() : metadata
        ));
    }

    private String memoryId(Long resumeId) {
        return "resume:" + resumeId;
    }

    private String memoryId(Long userId, String sessionId, Long resumeId) {
        String owner = userId == null ? "anonymous" : String.valueOf(userId);
        if (sessionId != null && !sessionId.isBlank()) {
            return "interview:" + owner + ":" + sessionId;
        }
        return "resume:" + owner + ":" + resumeId;
    }

    private String safeFilename(MultipartFile file) {
        String name = file == null ? null : file.getOriginalFilename();
        return StringUtils.hasText(name) ? name : "uploaded-resume";
    }

    private ResumeParseTaskResult toParseTaskResult(ResumeParseTaskRecord task) {
        return new ResumeParseTaskResult(
                task.taskId(),
                task.userId(),
                task.status().name(),
                task.status().message(),
                parseProgress(task),
                estimatedRemainingSeconds(task),
                task.resumeId(),
                task.originalFilename(),
                task.fileSize(),
                task.contentType(),
                task.storageProvider(),
                task.storageKey(),
                task.retryOfTaskId(),
                task.errorMessage(),
                task.startTime(),
                task.completeTime()
        );
    }

    private int parseProgress(ResumeParseTaskRecord task) {
        return switch (task.status()) {
            case PROCESSING -> 5;
            case ANALYZING -> 35;
            case SAVING -> 85;
            case COMPLETED -> 100;
            case FAILED, CANCELED -> 0;
        };
    }

    private Long estimatedRemainingSeconds(ResumeParseTaskRecord task) {
        return switch (task.status()) {
            case PROCESSING -> 65L;
            case ANALYZING -> 30L;
            case SAVING -> 5L;
            case COMPLETED, FAILED, CANCELED -> null;
        };
    }

    private record SnapshotMultipartFile(String originalFilename, String contentType, byte[] bytes) implements MultipartFile {
        private SnapshotMultipartFile {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public String getName() {
            return "resume";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File dest) throws java.io.IOException {
            try (FileOutputStream output = new FileOutputStream(dest)) {
                output.write(bytes);
            }
        }
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    private String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
