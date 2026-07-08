package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.hewei.hzyjy.xunzhi.career.agent.cv.CvOptimizationOrchestrator;
import com.hewei.hzyjy.xunzhi.career.agent.cv.CvOptimizationResult;
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
import com.hewei.hzyjy.xunzhi.career.resume.model.SkillBO;
import com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeChunk;
import com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;


import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import java.util.zip.ZipFile;
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeApplicationService {

    private static final Pattern XML_TAG = Pattern.compile("<[^>]+>");
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md", "markdown", "json", "csv", "log");
    private static final int MAX_RESUME_TEXT_LENGTH = 120000;
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DOCX_ENTRY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DOCX_TOTAL_UNCOMPRESSED_BYTES = 4 * 1024 * 1024;
    private static final int MAX_DOCX_ENTRIES = 128;
    private static final int MAX_PDF_PAGES = 40;
    private static final int MAX_JD_LENGTH = 12000;
    private static final int MAX_QUESTION_LENGTH = 4000;
    private static final int MAX_ANSWER_LENGTH = 12000;

    private final ResumeStore resumeStore;
    private final JobMatchTaskStore jobMatchTaskStore;
    private final ResumeRagService resumeRagService;
    private final CvOptimizationOrchestrator cvOptimizationOrchestrator;
    private final InterviewPlanningService interviewPlanningService;
    private final CareerInterviewExecutionBridge interviewExecutionBridge;
    private final HybridCompactingChatMemory chatMemory;
    private final ResumeStructuringService resumeStructuringService;
    private final ObjectProvider<AiTracePublisher> tracePublisherProvider;
    private final ConcurrentMap<Long, Boolean> embeddedResumeIds = new ConcurrentHashMap<>();

    public ResumeUploadResult upload(Long userId, MultipartFile file) {
        long start = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        try {
            CvBO parsed = parseUpload(userId, file);
            CvBO saved = resumeStore.save(parsed);
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

    public JobMatchTaskResult matchResumes(Long userId, String jobDescription, int limit) {
        validateTextLength(jobDescription, MAX_JD_LENGTH, "Job description");
        String taskId = UUID.randomUUID().toString();
        int boundedLimit = Math.min(limit <= 0 ? 3 : limit, 20);
        JobMatchTaskResult started = JobMatchTaskResult.started(taskId, userId);
        jobMatchTaskStore.save(started, jobDescription, boundedLimit, null);
        long start = System.currentTimeMillis();
        try {
            Set<String> resumeIds = ensureUserResumeEmbeddings(userId);
            List<String> templates = resumeRagService.retrieveTemplates(jobDescription, boundedLimit, userId, resumeIds);
            JobMatchTaskResult completed = JobMatchTaskResult.completed(taskId, userId, templates);
            publishTool(taskId, "job-match:" + taskId, "JD_ALIGNMENT", "resume-rag-match", jobDescription,
                    "matched=" + templates.size(), true, start, null, Map.of("userId", userId, "resumeScope", resumeIds.size()));
            return jobMatchTaskStore.save(completed, jobDescription, boundedLimit, null);
        } catch (Exception ex) {
            log.warn("Career job match task failed. taskId={}", taskId, ex);
            JobMatchTaskResult failed = JobMatchTaskResult.failed(taskId, userId, ex.getMessage());
            publishTool(taskId, "job-match:" + taskId, "JD_ALIGNMENT", "resume-rag-match", jobDescription,
                    "FAILED", false, start, ex.getMessage(), Map.of("userId", userId));
            return jobMatchTaskStore.save(failed, jobDescription, boundedLimit, ex.getMessage());
        }
    }

    public JobMatchTaskResult getMatchTask(Long userId, String taskId) {
        return jobMatchTaskStore.findByTaskIdAndUserId(taskId, userId).orElseGet(() -> JobMatchTaskResult.notFound(taskId));
    }

    public CvOptimizationResult optimize(Long userId, Long resumeId, String jobDescription) {
        validateTextLength(jobDescription, MAX_JD_LENGTH, "Job description");
        CvBO cv = getResume(userId, resumeId);
        ensureResumeEmbedding(cv);
        List<String> templates = resumeRagService.retrieveTemplates(jobDescription, 3, userId, Set.of(String.valueOf(resumeId)));
        CvOptimizationResult result = cvOptimizationOrchestrator.optimize(cv, jobDescription, templates, 3);
        CvBO latest = result.cv() == null ? cv : result.cv().toBuilder().id(resumeId).userId(userId).build();
        if (result.scoreGatePassed()) {
            resumeStore.save(latest);
            embeddedResumeIds.remove(resumeId);
            ensureResumeEmbedding(latest);
        }
        chatMemory.add(memoryId(resumeId), MemoryMessage.builder()
                .role(MemoryRole.ASSISTANT)
                .content("Resume optimization decision: scoreGatePassed=" + result.scoreGatePassed()
                        + ", iterations=" + result.iterations()
                        + ", bestReview=" + result.bestReview())
                .metadata(Map.of("scene", "RESUME_TAILOR", "resumeId", String.valueOf(resumeId), "userId", String.valueOf(userId)))
                .build());
        return CvOptimizationResult.builder()
                .cv(latest)
                .bestReview(result.bestReview())
                .iterations(result.iterations())
                .scoreGatePassed(result.scoreGatePassed())
                .failureReason(result.failureReason())
                .reviewHistory(result.reviewHistory())
                .build();
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


    private ResumeEmbeddingResult embeddingOwned(Long userId, Long resumeId, boolean failOnError) {
        CvBO cv = getResume(userId, resumeId);
        return doEmbedding(cv, failOnError);
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
            log.warn("Resume embedding failed. resumeId={}", cv.getId(), ex);
            publishTool(traceId, memoryId(cv.getId()), "RESUME_ANALYSIS", "resume-embedding", cv.getName(),
                    "FAILED", false, start, ex.getMessage(), Map.of("resumeId", cv.getId(), "userId", cv.getUserId()));
            if (failOnError) {
                throw new IllegalStateException("Resume embedding failed: " + ex.getMessage(), ex);
            }
            return ResumeEmbeddingResult.failed(cv.getId(), ex.getMessage());
        }
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
                    return extractPdfText(input);
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

    private String extractPdfText(InputStream input) throws Exception {
        try (RandomAccessReadBuffer buffer = new RandomAccessReadBuffer(input);
             PDDocument document = Loader.loadPDF(buffer)) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("Encrypted PDF resumes are not supported");
            }
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new IllegalArgumentException("PDF resume exceeds " + MAX_PDF_PAGES + " page limit");
            }
            PDFTextStripper pdfStripper = new PDFTextStripper();
            pdfStripper.setSortByPosition(true);
            CappedResumeTextWriter writer = new CappedResumeTextWriter(MAX_RESUME_TEXT_LENGTH);
            try {
                pdfStripper.writeText(document, writer);
            } catch (ResumeTextLimitReachedException ignored) {
                log.debug("PDF resume text extraction stopped after reaching {} characters", MAX_RESUME_TEXT_LENGTH);
            }
            return writer.normalizedText();
        }
    }


    private static final class CappedResumeTextWriter extends Writer {
        private final StringBuilder buffer;
        private final int maxChars;

        private CappedResumeTextWriter(int maxChars) {
            this.maxChars = maxChars;
            this.buffer = new StringBuilder(Math.min(maxChars, 8192));
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            if (len <= 0) {
                return;
            }
            if (buffer.length() >= maxChars) {
                throw new ResumeTextLimitReachedException();
            }
            int writable = Math.min(len, maxChars - buffer.length());
            buffer.append(cbuf, off, writable);
            if (writable < len || buffer.length() >= maxChars) {
                throw new ResumeTextLimitReachedException();
            }
        }

        @Override
        public void flush() {
            // No external resource to flush.
        }

        @Override
        public void close() {
            // No external resource to close.
        }

        private String normalizedText() {
            return buffer.toString().replaceAll("\\s+", " ").trim();
        }
    }

    private static final class ResumeTextLimitReachedException extends RuntimeException {
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
                log.debug("Temporary DOCX file cleanup deferred. path={}", tempFile.getAbsolutePath());
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
