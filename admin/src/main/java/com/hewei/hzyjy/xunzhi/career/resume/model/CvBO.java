package com.hewei.hzyjy.xunzhi.career.resume.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CvBO {

    private Long id;

    private Long userId;

    private String cvType;

    private String name;

    private LocalDate birthDate;

    private String title;

    private String avatarUrl;

    private String summary;

    private ContactBO contact;

    @Builder.Default
    private List<SocialLinkBO> socialLinks = new ArrayList<>();

    @Builder.Default
    private List<EducationBO> educations = new ArrayList<>();

    @Builder.Default
    private List<ExperienceBO> experiences = new ArrayList<>();

    @Builder.Default
    private List<ProjectBO> projects = new ArrayList<>();

    @Builder.Default
    private List<SkillBO> skills = new ArrayList<>();

    @Builder.Default
    private List<CertificateBO> certificates = new ArrayList<>();

    private String advice;

    @Builder.Default
    private List<OptimizationRecord> optimizationHistory = new ArrayList<>();

    private FormatMetaBO meta;

    public void addOptimizationRecord(String feedback, Double score) {
        if (optimizationHistory == null) {
            optimizationHistory = new ArrayList<>();
        }
        optimizationHistory.add(OptimizationRecord.builder()
                .feedback(feedback)
                .score(score)
                .build());
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizationRecord {
        private String feedback;
        private Double score;
    }
}
