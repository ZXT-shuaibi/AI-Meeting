package com.hewei.hzyjy.xunzhi.career.resume.rag;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.EducationBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.ExperienceBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.HighlightBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.ProjectBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.SkillBO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.CHUNK_TYPE_EDUCATION;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.CHUNK_TYPE_EXPERIENCE;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.CHUNK_TYPE_OVERVIEW;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.CHUNK_TYPE_PROJECT;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.CHUNK_TYPE_SKILLS;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.CHUNK_TYPE_SUMMARY;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_CHUNK_INDEX;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_CHUNK_TYPE;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_COMPANIES;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_CV_TYPE;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_INDUSTRIES;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_RESUME_ID;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_ROLES;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_SKILL_NAMES;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_USER_ID;

@Component
public class ResumeChunker {

    public List<ResumeChunk> chunk(CvBO cv) {
        if (cv == null) {
            return List.of();
        }
        List<ResumeChunk> chunks = new ArrayList<>();
        String resumeId = resolveResumeId(cv);
        String cvType = defaultString(cv.getCvType(), "upload");

        String userId = cv.getUserId() == null ? "" : String.valueOf(cv.getUserId());
        ResumeChunk overview = buildOverviewChunk(cv, resumeId, userId, cvType);
        if (overview != null) {
            chunks.add(overview);
        }
        if (!isBlank(cv.getSummary())) {
            chunks.add(ResumeChunk.builder()
                    .chunkType(CHUNK_TYPE_SUMMARY)
                    .content("Summary:\n" + cv.getSummary())
                    .metadata(buildBaseMetadata(resumeId, userId, cvType, CHUNK_TYPE_SUMMARY, 0))
                    .build());
        }
        ResumeChunk skills = buildSkillsChunk(cv, resumeId, userId, cvType);
        if (skills != null) {
            chunks.add(skills);
        }
        for (int i = 0; i < safeList(cv.getExperiences()).size(); i++) {
            ResumeChunk chunk = buildExperienceChunk(safeList(cv.getExperiences()).get(i), resumeId, userId, cvType, i);
            if (chunk != null) {
                chunks.add(chunk);
            }
        }
        for (int i = 0; i < safeList(cv.getProjects()).size(); i++) {
            ResumeChunk chunk = buildProjectChunk(safeList(cv.getProjects()).get(i), resumeId, userId, cvType, i);
            if (chunk != null) {
                chunks.add(chunk);
            }
        }
        for (int i = 0; i < safeList(cv.getEducations()).size(); i++) {
            ResumeChunk chunk = buildEducationChunk(safeList(cv.getEducations()).get(i), resumeId, userId, cvType, i);
            if (chunk != null) {
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    public String resolveResumeId(CvBO cv) {
        if (cv.getId() != null) {
            return String.valueOf(cv.getId());
        }
        if (cv.getUserId() != null) {
            return String.valueOf(cv.getUserId());
        }
        return "tmpl_" + Math.abs(Objects.hash(cv.getName(), cv.getTitle(), cv.getSummary()));
    }

    private ResumeChunk buildOverviewChunk(CvBO cv, String resumeId, String userId, String cvType) {
        StringBuilder content = new StringBuilder("Resume Overview\n");
        appendLine(content, "Name", cv.getName());
        appendLine(content, "Title", cv.getTitle());
        appendLine(content, "Summary", abbreviate(cv.getSummary(), 300));

        List<String> skillNames = safeList(cv.getSkills()).stream()
                .map(SkillBO::getName)
                .filter(s -> !isBlank(s))
                .toList();
        List<String> industries = safeList(cv.getExperiences()).stream()
                .map(ExperienceBO::getIndustry)
                .filter(s -> !isBlank(s))
                .toList();
        List<String> companies = safeList(cv.getExperiences()).stream()
                .map(ExperienceBO::getCompany)
                .filter(s -> !isBlank(s))
                .toList();
        List<String> roles = safeList(cv.getExperiences()).stream()
                .map(ExperienceBO::getRole)
                .filter(s -> !isBlank(s))
                .toList();
        appendLine(content, "Key Skills", String.join(", ", skillNames));
        appendLine(content, "Industries", String.join(", ", industries));
        appendLine(content, "Companies", String.join(", ", companies));
        appendLine(content, "Roles", String.join(", ", roles));

        if (content.toString().trim().equals("Resume Overview")) {
            return null;
        }
        Map<String, String> metadata = buildBaseMetadata(resumeId, userId, cvType, CHUNK_TYPE_OVERVIEW, 0);
        metadata.put(META_SKILL_NAMES, String.join(", ", skillNames));
        metadata.put(META_INDUSTRIES, String.join(", ", industries));
        metadata.put(META_COMPANIES, String.join(", ", companies));
        metadata.put(META_ROLES, String.join(", ", roles));
        return ResumeChunk.builder().chunkType(CHUNK_TYPE_OVERVIEW).content(content.toString().trim()).metadata(metadata).build();
    }

    private ResumeChunk buildSkillsChunk(CvBO cv, String resumeId, String userId, String cvType) {
        List<SkillBO> skills = safeList(cv.getSkills());
        if (skills.isEmpty()) {
            return null;
        }
        StringBuilder content = new StringBuilder("Skills:\n");
        List<String> skillNames = new ArrayList<>();
        for (SkillBO skill : skills) {
            if (skill == null || isBlank(skill.getName())) {
                continue;
            }
            skillNames.add(skill.getName());
            content.append("- ").append(skill.getName());
            if (!isBlank(skill.getLevel())) {
                content.append(" (").append(skill.getLevel()).append(")");
            }
            appendHighlights(content, skill.getHighlights());
            content.append("\n");
        }
        if (skillNames.isEmpty()) {
            return null;
        }
        Map<String, String> metadata = buildBaseMetadata(resumeId, userId, cvType, CHUNK_TYPE_SKILLS, 0);
        metadata.put(META_SKILL_NAMES, String.join(", ", skillNames));
        return ResumeChunk.builder().chunkType(CHUNK_TYPE_SKILLS).content(content.toString().trim()).metadata(metadata).build();
    }

    private ResumeChunk buildExperienceChunk(ExperienceBO exp, String resumeId, String userId, String cvType, int index) {
        if (exp == null) {
            return null;
        }
        StringBuilder content = new StringBuilder("Work Experience:\n");
        content.append("- ").append(defaultString(exp.getCompany(), "Unknown Company"));
        appendInline(content, exp.getRole());
        appendLine(content, "Industry", exp.getIndustry());
        appendLine(content, "Description", exp.getDescription());
        appendHighlights(content, exp.getHighlights());

        Map<String, String> metadata = buildBaseMetadata(resumeId, userId, cvType, CHUNK_TYPE_EXPERIENCE, index);
        metadata.put(META_INDUSTRIES, defaultString(exp.getIndustry(), ""));
        metadata.put(META_COMPANIES, defaultString(exp.getCompany(), ""));
        metadata.put(META_ROLES, defaultString(exp.getRole(), ""));
        return ResumeChunk.builder().chunkType(CHUNK_TYPE_EXPERIENCE).content(content.toString().trim()).metadata(metadata).build();
    }

    private ResumeChunk buildProjectChunk(ProjectBO project, String resumeId, String userId, String cvType, int index) {
        if (project == null) {
            return null;
        }
        StringBuilder content = new StringBuilder("Project:\n");
        content.append("- ").append(defaultString(project.getName(), "Unnamed Project"));
        appendInline(content, project.getRole());
        appendLine(content, "Description", project.getDescription());
        appendHighlights(content, project.getHighlights());

        Map<String, String> metadata = buildBaseMetadata(resumeId, userId, cvType, CHUNK_TYPE_PROJECT, index);
        metadata.put(META_ROLES, defaultString(project.getRole(), ""));
        return ResumeChunk.builder().chunkType(CHUNK_TYPE_PROJECT).content(content.toString().trim()).metadata(metadata).build();
    }

    private ResumeChunk buildEducationChunk(EducationBO education, String resumeId, String userId, String cvType, int index) {
        if (education == null) {
            return null;
        }
        StringBuilder content = new StringBuilder("Education:\n");
        content.append("- ").append(defaultString(education.getSchool(), "Unknown School"));
        appendInline(content, education.getMajor());
        appendInline(content, education.getDegree());
        appendLine(content, "Description", education.getDescription());
        return ResumeChunk.builder()
                .chunkType(CHUNK_TYPE_EDUCATION)
                .content(content.toString().trim())
                .metadata(buildBaseMetadata(resumeId, userId, cvType, CHUNK_TYPE_EDUCATION, index))
                .build();
    }

    private Map<String, String> buildBaseMetadata(String resumeId, String userId, String cvType, String chunkType, int index) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(META_RESUME_ID, resumeId);
        metadata.put(META_USER_ID, userId == null ? "" : userId);
        metadata.put(META_CV_TYPE, cvType);
        metadata.put(META_CHUNK_TYPE, chunkType);
        metadata.put(META_CHUNK_INDEX, String.valueOf(index));
        return metadata;
    }

    private void appendLine(StringBuilder content, String label, String value) {
        if (!isBlank(value)) {
            content.append(label).append(": ").append(value).append("\n");
        }
    }

    private void appendInline(StringBuilder content, String value) {
        if (!isBlank(value)) {
            content.append(" (").append(value).append(")");
        }
    }

    private void appendHighlights(StringBuilder content, List<HighlightBO> highlights) {
        safeList(highlights).stream()
                .map(HighlightBO::getHighlight)
                .filter(s -> !isBlank(s))
                .forEach(highlight -> content.append(" Highlight: ").append(highlight));
    }

    private String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String defaultString(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
