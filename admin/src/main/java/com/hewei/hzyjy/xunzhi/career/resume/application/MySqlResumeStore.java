package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeCertificateDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeContactDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeEducationDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeExperienceDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeFormatMetaDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeHighlightDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeLocaleConfigDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeProjectDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeSkillDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeSocialLinkDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeCertificateMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeContactMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeEducationMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeExperienceMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeFormatMetaMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeHighlightMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeLocaleConfigMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeProjectMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeSkillMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeSocialLinkMapper;
import com.hewei.hzyjy.xunzhi.career.resume.model.CertificateBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.ContactBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.EducationBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.ExperienceBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.FormatMetaBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.HighlightBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.LocaleConfigBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.ProjectBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.SkillBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.SocialLinkBO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class MySqlResumeStore implements ResumeStore {

    private final ObjectProvider<CareerResumeMapper> mapperProvider;
    private final ObjectProvider<CareerResumeContactMapper> contactMapperProvider;
    private final ObjectProvider<CareerResumeEducationMapper> educationMapperProvider;
    private final ObjectProvider<CareerResumeExperienceMapper> experienceMapperProvider;
    private final ObjectProvider<CareerResumeProjectMapper> projectMapperProvider;
    private final ObjectProvider<CareerResumeSkillMapper> skillMapperProvider;
    private final ObjectProvider<CareerResumeSocialLinkMapper> socialLinkMapperProvider;
    private final ObjectProvider<CareerResumeCertificateMapper> certificateMapperProvider;
    private final ObjectProvider<CareerResumeFormatMetaMapper> formatMetaMapperProvider;
    private final ObjectProvider<CareerResumeLocaleConfigMapper> localeConfigMapperProvider;
    private final ObjectProvider<CareerResumeHighlightMapper> highlightMapperProvider;
    private final InMemoryResumeStore fallbackStore;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CvBO save(CvBO cv) {
        CareerResumeMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return fallbackStore.save(cv);
        }
        try {
            CareerResumeDO row = toRow(cv);
            Date now = new Date();
            row.setUpdateTime(now);
            row.setDelFlag(0);
            if (row.getId() == null) {
                row.setCreateTime(now);
                mapper.insert(row);
            } else {
                CareerResumeDO existing = mapper.selectById(row.getId());
                if (existing == null || Integer.valueOf(1).equals(existing.getDelFlag())) {
                    row.setCreateTime(now);
                    mapper.insert(row);
                } else {
                    row.setCreateTime(existing.getCreateTime());
                    mapper.updateById(row);
                }
            }
            CvBO saved = cv.toBuilder().id(row.getId()).build();
            replaceDetails(saved, now);
            fallbackStore.save(saved);
            return saved;
        } catch (Exception ex) {
            log.warn("MySQL resume store failed, using in-memory fallback. resumeId={}", cv == null ? null : cv.getId(), ex);
            return fallbackStore.save(cv);
        }
    }

    @Override
    public Optional<CvBO> findById(Long resumeId) {
        CareerResumeMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                CareerResumeDO row = mapper.selectOne(Wrappers.<CareerResumeDO>lambdaQuery()
                        .eq(CareerResumeDO::getId, resumeId)
                        .eq(CareerResumeDO::getDelFlag, 0)
                        .last("limit 1"));
                if (row != null) {
                    CvBO cv = withDetails(fromRow(row));
                    fallbackStore.save(cv);
                    return Optional.of(cv);
                }
            } catch (Exception ex) {
                log.warn("MySQL resume lookup failed, using in-memory fallback. resumeId={}", resumeId, ex);
            }
        }
        return fallbackStore.findById(resumeId);
    }

    @Override
    public Optional<CvBO> findByIdAndUserId(Long resumeId, Long userId) {
        if (resumeId == null || userId == null) {
            return Optional.empty();
        }
        CareerResumeMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                CareerResumeDO row = mapper.selectOne(Wrappers.<CareerResumeDO>lambdaQuery()
                        .eq(CareerResumeDO::getId, resumeId)
                        .eq(CareerResumeDO::getUserId, userId)
                        .eq(CareerResumeDO::getDelFlag, 0)
                        .last("limit 1"));
                if (row != null) {
                    CvBO cv = withDetails(fromRow(row));
                    fallbackStore.save(cv);
                    return Optional.of(cv);
                }
                return Optional.empty();
            } catch (Exception ex) {
                log.warn("MySQL resume owner lookup failed, using in-memory fallback. resumeId={}, userId={}", resumeId, userId, ex);
            }
        }
        return fallbackStore.findByIdAndUserId(resumeId, userId);
    }

    @Override
    public List<CvBO> findByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        CareerResumeMapper mapper = mapperProvider.getIfAvailable();
        if (mapper != null) {
            try {
                List<CvBO> resumes = mapper.selectList(Wrappers.<CareerResumeDO>lambdaQuery()
                                .eq(CareerResumeDO::getUserId, userId)
                                .eq(CareerResumeDO::getDelFlag, 0)
                                .orderByDesc(CareerResumeDO::getUpdateTime))
                        .stream()
                        .map(this::fromRow)
                        .map(this::withDetails)
                        .toList();
                resumes.forEach(fallbackStore::save);
                return resumes;
            } catch (Exception ex) {
                log.warn("MySQL resume list failed, using in-memory fallback. userId={}", userId, ex);
            }
        }
        return fallbackStore.findByUserId(userId);
    }

    private void replaceDetails(CvBO cv, Date now) {
        Long resumeId = cv.getId();
        if (resumeId == null) {
            return;
        }
        CareerResumeContactMapper contactMapper = contactMapperProvider.getIfAvailable();
        CareerResumeEducationMapper educationMapper = educationMapperProvider.getIfAvailable();
        CareerResumeExperienceMapper experienceMapper = experienceMapperProvider.getIfAvailable();
        CareerResumeProjectMapper projectMapper = projectMapperProvider.getIfAvailable();
        CareerResumeSkillMapper skillMapper = skillMapperProvider.getIfAvailable();
        CareerResumeSocialLinkMapper socialLinkMapper = socialLinkMapperProvider.getIfAvailable();
        CareerResumeCertificateMapper certificateMapper = certificateMapperProvider.getIfAvailable();
        CareerResumeFormatMetaMapper formatMetaMapper = formatMetaMapperProvider.getIfAvailable();
        CareerResumeLocaleConfigMapper localeConfigMapper = localeConfigMapperProvider.getIfAvailable();
        CareerResumeHighlightMapper highlightMapper = highlightMapperProvider.getIfAvailable();
        List<SocialLinkBO> socialLinks = cv.getSocialLinks() == null ? List.of() : cv.getSocialLinks();
        List<EducationBO> educations = cv.getEducations() == null ? List.of() : cv.getEducations();
        List<ExperienceBO> experiences = cv.getExperiences() == null ? List.of() : cv.getExperiences();
        List<ProjectBO> projects = cv.getProjects() == null ? List.of() : cv.getProjects();
        List<SkillBO> skills = cv.getSkills() == null ? List.of() : cv.getSkills();
        List<CertificateBO> certificates = cv.getCertificates() == null ? List.of() : cv.getCertificates();
        if (contactMapper != null) {
            contactMapper.delete(Wrappers.<CareerResumeContactDO>lambdaQuery().eq(CareerResumeContactDO::getResumeId, resumeId));
            if (cv.getContact() != null) {
                CareerResumeContactDO row = toContactRow(resumeId, cv.getContact(), now);
                contactMapper.insert(row);
            }
        }
        if (educationMapper != null) {
            educationMapper.delete(Wrappers.<CareerResumeEducationDO>lambdaQuery().eq(CareerResumeEducationDO::getResumeId, resumeId));
            for (int i = 0; i < educations.size(); i++) {
                educationMapper.insert(toEducationRow(resumeId, i, educations.get(i), now));
            }
        }
        if (experienceMapper != null) {
            experienceMapper.delete(Wrappers.<CareerResumeExperienceDO>lambdaQuery().eq(CareerResumeExperienceDO::getResumeId, resumeId));
            for (int i = 0; i < experiences.size(); i++) {
                experienceMapper.insert(toExperienceRow(resumeId, i, experiences.get(i), now));
            }
        }
        if (projectMapper != null) {
            projectMapper.delete(Wrappers.<CareerResumeProjectDO>lambdaQuery().eq(CareerResumeProjectDO::getResumeId, resumeId));
            for (int i = 0; i < projects.size(); i++) {
                projectMapper.insert(toProjectRow(resumeId, i, projects.get(i), now));
            }
        }
        if (skillMapper != null) {
            skillMapper.delete(Wrappers.<CareerResumeSkillDO>lambdaQuery().eq(CareerResumeSkillDO::getResumeId, resumeId));
            for (int i = 0; i < skills.size(); i++) {
                skillMapper.insert(toSkillRow(resumeId, i, skills.get(i), now));
            }
        }
        if (socialLinkMapper != null) {
            try {
                socialLinkMapper.delete(Wrappers.<CareerResumeSocialLinkDO>lambdaQuery().eq(CareerResumeSocialLinkDO::getResumeId, resumeId));
                for (int i = 0; i < socialLinks.size(); i++) {
                    socialLinkMapper.insert(toSocialLinkRow(resumeId, i, socialLinks.get(i), now));
                }
            } catch (Exception ex) {
                log.warn("Optional resume social link persistence failed, continuing with cv_json. resumeId={}", resumeId, ex);
            }
        }
        if (certificateMapper != null) {
            try {
                certificateMapper.delete(Wrappers.<CareerResumeCertificateDO>lambdaQuery().eq(CareerResumeCertificateDO::getResumeId, resumeId));
                for (int i = 0; i < certificates.size(); i++) {
                    certificateMapper.insert(toCertificateRow(resumeId, i, certificates.get(i), now));
                }
            } catch (Exception ex) {
                log.warn("Optional resume certificate persistence failed, continuing with cv_json. resumeId={}", resumeId, ex);
            }
        }
        if (formatMetaMapper != null) {
            try {
                formatMetaMapper.delete(Wrappers.<CareerResumeFormatMetaDO>lambdaQuery().eq(CareerResumeFormatMetaDO::getResumeId, resumeId));
                if (cv.getMeta() != null) {
                    formatMetaMapper.insert(toFormatMetaRow(resumeId, cv.getMeta(), now));
                }
            } catch (Exception ex) {
                log.warn("Optional resume format metadata persistence failed, continuing with cv_json. resumeId={}", resumeId, ex);
            }
        }
        if (localeConfigMapper != null) {
            try {
                localeConfigMapper.delete(Wrappers.<CareerResumeLocaleConfigDO>lambdaQuery().eq(CareerResumeLocaleConfigDO::getResumeId, resumeId));
                if (cv.getMeta() != null && cv.getMeta().getLocaleConfig() != null) {
                    localeConfigMapper.insert(toLocaleConfigRow(resumeId, cv.getMeta().getLocaleConfig(), now));
                }
            } catch (Exception ex) {
                log.warn("Optional resume locale config persistence failed, continuing with cv_json. resumeId={}", resumeId, ex);
            }
        }
        if (highlightMapper != null) {
            try {
                highlightMapper.delete(Wrappers.<CareerResumeHighlightDO>lambdaQuery().eq(CareerResumeHighlightDO::getResumeId, resumeId));
                insertHighlightRows(highlightMapper, resumeId, "experience", experiences, now);
                insertHighlightRows(highlightMapper, resumeId, "project", projects, now);
                insertHighlightRows(highlightMapper, resumeId, "skill", skills, now);
            } catch (Exception ex) {
                log.warn("Optional resume highlight persistence failed, continuing with highlights_json/cv_json. resumeId={}", resumeId, ex);
            }
        }
    }

    private CvBO withDetails(CvBO cv) {
        if (cv == null || cv.getId() == null) {
            return cv;
        }
        CvBO.CvBOBuilder builder = cv.toBuilder();
        Map<String, List<HighlightBO>> highlightsByOwner = loadHighlights(cv.getId());
        CareerResumeContactMapper contactMapper = contactMapperProvider.getIfAvailable();
        if (contactMapper != null) {
            CareerResumeContactDO row = contactMapper.selectOne(Wrappers.<CareerResumeContactDO>lambdaQuery()
                    .eq(CareerResumeContactDO::getResumeId, cv.getId())
                    .eq(CareerResumeContactDO::getDelFlag, 0)
                    .last("limit 1"));
            if (row != null) {
                builder.contact(fromContactRow(row));
            }
        }
        CareerResumeSocialLinkMapper socialLinkMapper = socialLinkMapperProvider.getIfAvailable();
        if (socialLinkMapper != null) {
            try {
                List<SocialLinkBO> rows = socialLinkMapper.selectList(Wrappers.<CareerResumeSocialLinkDO>lambdaQuery()
                                .eq(CareerResumeSocialLinkDO::getResumeId, cv.getId())
                                .eq(CareerResumeSocialLinkDO::getDelFlag, 0)
                                .orderByAsc(CareerResumeSocialLinkDO::getItemIndex))
                        .stream()
                        .map(this::fromSocialLinkRow)
                        .toList();
                if (!rows.isEmpty()) {
                    builder.socialLinks(rows);
                }
            } catch (Exception ex) {
                log.warn("Optional resume social link lookup failed, using cv_json value. resumeId={}", cv.getId(), ex);
            }
        }
        CareerResumeEducationMapper educationMapper = educationMapperProvider.getIfAvailable();
        if (educationMapper != null) {
            List<EducationBO> rows = educationMapper.selectList(Wrappers.<CareerResumeEducationDO>lambdaQuery()
                            .eq(CareerResumeEducationDO::getResumeId, cv.getId())
                            .eq(CareerResumeEducationDO::getDelFlag, 0)
                            .orderByAsc(CareerResumeEducationDO::getItemIndex))
                    .stream()
                    .map(this::fromEducationRow)
                    .toList();
            if (!rows.isEmpty()) {
                builder.educations(rows);
            }
        }
        CareerResumeExperienceMapper experienceMapper = experienceMapperProvider.getIfAvailable();
        if (experienceMapper != null) {
            List<ExperienceBO> rows = experienceMapper.selectList(Wrappers.<CareerResumeExperienceDO>lambdaQuery()
                            .eq(CareerResumeExperienceDO::getResumeId, cv.getId())
                            .eq(CareerResumeExperienceDO::getDelFlag, 0)
                            .orderByAsc(CareerResumeExperienceDO::getItemIndex))
                    .stream()
                    .map(row -> fromExperienceRow(row, highlightsByOwner.get(highlightKey("experience", row.getItemIndex()))))
                    .toList();
            if (!rows.isEmpty()) {
                builder.experiences(rows);
            }
        }
        CareerResumeProjectMapper projectMapper = projectMapperProvider.getIfAvailable();
        if (projectMapper != null) {
            List<ProjectBO> rows = projectMapper.selectList(Wrappers.<CareerResumeProjectDO>lambdaQuery()
                            .eq(CareerResumeProjectDO::getResumeId, cv.getId())
                            .eq(CareerResumeProjectDO::getDelFlag, 0)
                            .orderByAsc(CareerResumeProjectDO::getItemIndex))
                    .stream()
                    .map(row -> fromProjectRow(row, highlightsByOwner.get(highlightKey("project", row.getItemIndex()))))
                    .toList();
            if (!rows.isEmpty()) {
                builder.projects(rows);
            }
        }
        CareerResumeSkillMapper skillMapper = skillMapperProvider.getIfAvailable();
        if (skillMapper != null) {
            List<SkillBO> rows = skillMapper.selectList(Wrappers.<CareerResumeSkillDO>lambdaQuery()
                            .eq(CareerResumeSkillDO::getResumeId, cv.getId())
                            .eq(CareerResumeSkillDO::getDelFlag, 0)
                            .orderByAsc(CareerResumeSkillDO::getItemIndex))
                    .stream()
                    .map(row -> fromSkillRow(row, highlightsByOwner.get(highlightKey("skill", row.getItemIndex()))))
                    .toList();
            if (!rows.isEmpty()) {
                builder.skills(rows);
            }
        }
        CareerResumeCertificateMapper certificateMapper = certificateMapperProvider.getIfAvailable();
        if (certificateMapper != null) {
            try {
                List<CertificateBO> rows = certificateMapper.selectList(Wrappers.<CareerResumeCertificateDO>lambdaQuery()
                                .eq(CareerResumeCertificateDO::getResumeId, cv.getId())
                                .eq(CareerResumeCertificateDO::getDelFlag, 0)
                                .orderByAsc(CareerResumeCertificateDO::getItemIndex))
                        .stream()
                        .map(this::fromCertificateRow)
                        .toList();
                if (!rows.isEmpty()) {
                    builder.certificates(rows);
                }
            } catch (Exception ex) {
                log.warn("Optional resume certificate lookup failed, using cv_json value. resumeId={}", cv.getId(), ex);
            }
        }
        CareerResumeFormatMetaDO metaRow = null;
        CareerResumeFormatMetaMapper formatMetaMapper = formatMetaMapperProvider.getIfAvailable();
        if (formatMetaMapper != null) {
            try {
                metaRow = formatMetaMapper.selectOne(Wrappers.<CareerResumeFormatMetaDO>lambdaQuery()
                        .eq(CareerResumeFormatMetaDO::getResumeId, cv.getId())
                        .eq(CareerResumeFormatMetaDO::getDelFlag, 0)
                        .last("limit 1"));
            } catch (Exception ex) {
                log.warn("Optional resume format metadata lookup failed, using cv_json value. resumeId={}", cv.getId(), ex);
            }
        }
        CareerResumeLocaleConfigDO localeRow = null;
        CareerResumeLocaleConfigMapper localeConfigMapper = localeConfigMapperProvider.getIfAvailable();
        if (localeConfigMapper != null) {
            try {
                localeRow = localeConfigMapper.selectOne(Wrappers.<CareerResumeLocaleConfigDO>lambdaQuery()
                        .eq(CareerResumeLocaleConfigDO::getResumeId, cv.getId())
                        .eq(CareerResumeLocaleConfigDO::getDelFlag, 0)
                        .last("limit 1"));
            } catch (Exception ex) {
                log.warn("Optional resume locale config lookup failed, using cv_json value. resumeId={}", cv.getId(), ex);
            }
        }
        if (metaRow != null || localeRow != null) {
            builder.meta(fromFormatMetaRow(metaRow, localeRow));
        }
        return builder.build();
    }

    private CareerResumeDO toRow(CvBO cv) {
        CareerResumeDO row = new CareerResumeDO();
        row.setId(cv.getId());
        row.setUserId(cv.getUserId());
        row.setCvType(defaultString(cv.getCvType(), "upload"));
        row.setName(cv.getName());
        row.setTitle(cv.getTitle());
        row.setSummary(cv.getSummary());
        row.setCvJson(JSON.toJSONString(cv));
        return row;
    }

    private CvBO fromRow(CareerResumeDO row) {
        CvBO cv = JSON.parseObject(row.getCvJson(), CvBO.class);
        if (cv == null) {
            cv = CvBO.builder().build();
        }
        return cv.toBuilder()
                .id(row.getId())
                .userId(row.getUserId())
                .cvType(defaultString(row.getCvType(), cv.getCvType()))
                .name(defaultString(row.getName(), cv.getName()))
                .title(defaultString(row.getTitle(), cv.getTitle()))
                .summary(defaultString(row.getSummary(), cv.getSummary()))
                .build();
    }

    private CareerResumeContactDO toContactRow(Long resumeId, ContactBO contact, Date now) {
        CareerResumeContactDO row = new CareerResumeContactDO();
        setAudit(row, now);
        row.setResumeId(resumeId);
        row.setPhone(contact.getPhone());
        row.setEmail(contact.getEmail());
        row.setLocation(contact.getLocation());
        row.setWebsite(contact.getWebsite());
        return row;
    }

    private CareerResumeEducationDO toEducationRow(Long resumeId, int index, EducationBO education, Date now) {
        CareerResumeEducationDO row = new CareerResumeEducationDO();
        setAudit(row, now);
        row.setResumeId(resumeId);
        row.setItemIndex(index);
        row.setSchool(education.getSchool());
        row.setMajor(education.getMajor());
        row.setDegree(education.getDegree());
        row.setStartDate(education.getStartDate());
        row.setEndDate(education.getEndDate());
        row.setDescription(education.getDescription());
        return row;
    }

    private CareerResumeExperienceDO toExperienceRow(Long resumeId, int index, ExperienceBO experience, Date now) {
        CareerResumeExperienceDO row = new CareerResumeExperienceDO();
        setAudit(row, now);
        row.setResumeId(resumeId);
        row.setItemIndex(index);
        row.setCompany(experience.getCompany());
        row.setIndustry(experience.getIndustry());
        row.setRole(experience.getRole());
        row.setStartDate(experience.getStartDate());
        row.setEndDate(experience.getEndDate());
        row.setDescription(experience.getDescription());
        row.setHighlightsJson(JSON.toJSONString(experience.getHighlights() == null ? List.of() : experience.getHighlights()));
        return row;
    }

    private CareerResumeProjectDO toProjectRow(Long resumeId, int index, ProjectBO project, Date now) {
        CareerResumeProjectDO row = new CareerResumeProjectDO();
        setAudit(row, now);
        row.setResumeId(resumeId);
        row.setItemIndex(index);
        row.setName(project.getName());
        row.setRole(project.getRole());
        row.setStartDate(project.getStartDate());
        row.setEndDate(project.getEndDate());
        row.setDescription(project.getDescription());
        row.setHighlightsJson(JSON.toJSONString(project.getHighlights() == null ? List.of() : project.getHighlights()));
        return row;
    }

    private CareerResumeSkillDO toSkillRow(Long resumeId, int index, SkillBO skill, Date now) {
        CareerResumeSkillDO row = new CareerResumeSkillDO();
        setAudit(row, now);
        row.setResumeId(resumeId);
        row.setItemIndex(index);
        row.setCategory(skill.getCategory());
        row.setName(skill.getName());
        row.setLevel(skill.getLevel());
        row.setHighlightsJson(JSON.toJSONString(skill.getHighlights() == null ? List.of() : skill.getHighlights()));
        return row;
    }

    private CareerResumeSocialLinkDO toSocialLinkRow(Long resumeId, int index, SocialLinkBO socialLink, Date now) {
        CareerResumeSocialLinkDO row = new CareerResumeSocialLinkDO();
        setAudit(row, now);
        row.setResumeId(resumeId);
        row.setItemIndex(index);
        row.setName(socialLink.getName());
        row.setUrl(socialLink.getUrl());
        return row;
    }

    private CareerResumeCertificateDO toCertificateRow(Long resumeId, int index, CertificateBO certificate, Date now) {
        CareerResumeCertificateDO row = new CareerResumeCertificateDO();
        setAudit(row, now);
        row.setResumeId(resumeId);
        row.setItemIndex(index);
        row.setName(certificate.getName());
        row.setIssuer(certificate.getIssuer());
        row.setIssueDate(certificate.getIssueDate());
        row.setDescription(certificate.getDescription());
        return row;
    }

    private CareerResumeFormatMetaDO toFormatMetaRow(Long resumeId, FormatMetaBO meta, Date now) {
        CareerResumeFormatMetaDO row = new CareerResumeFormatMetaDO();
        setAudit(row, now);
        row.setResumeId(resumeId);
        row.setAlignment(meta.getAlignment());
        row.setLineSpacing(meta.getLineSpacing());
        row.setFontFamily(meta.getFontFamily());
        row.setDatePattern(meta.getDatePattern());
        row.setHyperlinkStyle(meta.getHyperlinkStyle());
        row.setShowAvatar(meta.getShowAvatar());
        row.setShowSocial(meta.getShowSocial());
        row.setTwoColumnLayout(meta.getTwoColumnLayout());
        return row;
    }

    private CareerResumeLocaleConfigDO toLocaleConfigRow(Long resumeId, LocaleConfigBO localeConfig, Date now) {
        CareerResumeLocaleConfigDO row = new CareerResumeLocaleConfigDO();
        setAudit(row, now);
        row.setResumeId(resumeId);
        row.setLocale(localeConfig.getLocale());
        row.setDatePattern(localeConfig.getDatePattern());
        row.setSectionLabels(localeConfig.getSectionLabels());
        return row;
    }

    private void insertHighlightRows(
            CareerResumeHighlightMapper highlightMapper,
            Long resumeId,
            String ownerType,
            List<?> owners,
            Date now) {
        if (owners == null || owners.isEmpty()) {
            return;
        }
        for (int ownerIndex = 0; ownerIndex < owners.size(); ownerIndex++) {
            List<HighlightBO> highlights = highlightsOf(owners.get(ownerIndex));
            for (int highlightIndex = 0; highlightIndex < highlights.size(); highlightIndex++) {
                highlightMapper.insert(toHighlightRow(resumeId, ownerType, ownerIndex, highlightIndex, highlights.get(highlightIndex), now));
            }
        }
    }

    private CareerResumeHighlightDO toHighlightRow(
            Long resumeId,
            String ownerType,
            int ownerIndex,
            int itemIndex,
            HighlightBO highlight,
            Date now) {
        CareerResumeHighlightDO row = new CareerResumeHighlightDO();
        setAudit(row, now);
        row.setResumeId(resumeId);
        row.setOwnerType(ownerType);
        row.setOwnerIndex(ownerIndex);
        row.setItemIndex(itemIndex);
        row.setType(highlight.getType());
        row.setRelatedId(highlight.getRelatedId());
        row.setHighlight(highlight.getHighlight());
        return row;
    }

    private List<HighlightBO> highlightsOf(Object owner) {
        if (owner instanceof ExperienceBO experience && experience.getHighlights() != null) {
            return experience.getHighlights();
        }
        if (owner instanceof ProjectBO project && project.getHighlights() != null) {
            return project.getHighlights();
        }
        if (owner instanceof SkillBO skill && skill.getHighlights() != null) {
            return skill.getHighlights();
        }
        return List.of();
    }

    private ContactBO fromContactRow(CareerResumeContactDO row) {
        return ContactBO.builder()
                .phone(row.getPhone())
                .email(row.getEmail())
                .location(row.getLocation())
                .website(row.getWebsite())
                .build();
    }

    private EducationBO fromEducationRow(CareerResumeEducationDO row) {
        return EducationBO.builder()
                .school(row.getSchool())
                .major(row.getMajor())
                .degree(row.getDegree())
                .startDate(row.getStartDate())
                .endDate(row.getEndDate())
                .description(row.getDescription())
                .build();
    }

    private ExperienceBO fromExperienceRow(CareerResumeExperienceDO row) {
        return fromExperienceRow(row, null);
    }

    private ExperienceBO fromExperienceRow(CareerResumeExperienceDO row, List<HighlightBO> highlights) {
        return ExperienceBO.builder()
                .company(row.getCompany())
                .industry(row.getIndustry())
                .role(row.getRole())
                .startDate(row.getStartDate())
                .endDate(row.getEndDate())
                .description(row.getDescription())
                .highlights(detailsOrJsonHighlights(highlights, row.getHighlightsJson()))
                .build();
    }

    private ProjectBO fromProjectRow(CareerResumeProjectDO row) {
        return fromProjectRow(row, null);
    }

    private ProjectBO fromProjectRow(CareerResumeProjectDO row, List<HighlightBO> highlights) {
        return ProjectBO.builder()
                .name(row.getName())
                .role(row.getRole())
                .startDate(row.getStartDate())
                .endDate(row.getEndDate())
                .description(row.getDescription())
                .highlights(detailsOrJsonHighlights(highlights, row.getHighlightsJson()))
                .build();
    }

    private SkillBO fromSkillRow(CareerResumeSkillDO row) {
        return fromSkillRow(row, null);
    }

    private SkillBO fromSkillRow(CareerResumeSkillDO row, List<HighlightBO> highlights) {
        return SkillBO.builder()
                .category(row.getCategory())
                .name(row.getName())
                .level(row.getLevel())
                .highlights(detailsOrJsonHighlights(highlights, row.getHighlightsJson()))
                .build();
    }

    private SocialLinkBO fromSocialLinkRow(CareerResumeSocialLinkDO row) {
        return SocialLinkBO.builder()
                .name(row.getName())
                .url(row.getUrl())
                .build();
    }

    private CertificateBO fromCertificateRow(CareerResumeCertificateDO row) {
        return CertificateBO.builder()
                .name(row.getName())
                .issuer(row.getIssuer())
                .issueDate(row.getIssueDate())
                .description(row.getDescription())
                .build();
    }

    private FormatMetaBO fromFormatMetaRow(CareerResumeFormatMetaDO row, CareerResumeLocaleConfigDO localeRow) {
        return FormatMetaBO.builder()
                .alignment(row == null ? null : row.getAlignment())
                .lineSpacing(row == null ? null : row.getLineSpacing())
                .fontFamily(row == null ? null : row.getFontFamily())
                .datePattern(row == null ? null : row.getDatePattern())
                .hyperlinkStyle(row == null ? null : row.getHyperlinkStyle())
                .showAvatar(row == null ? null : row.getShowAvatar())
                .showSocial(row == null ? null : row.getShowSocial())
                .twoColumnLayout(row == null ? null : row.getTwoColumnLayout())
                .localeConfig(fromLocaleConfigRow(localeRow))
                .build();
    }

    private LocaleConfigBO fromLocaleConfigRow(CareerResumeLocaleConfigDO row) {
        if (row == null) {
            return null;
        }
        return LocaleConfigBO.builder()
                .locale(row.getLocale())
                .datePattern(row.getDatePattern())
                .sectionLabels(row.getSectionLabels())
                .build();
    }

    private Map<String, List<HighlightBO>> loadHighlights(Long resumeId) {
        CareerResumeHighlightMapper highlightMapper = highlightMapperProvider.getIfAvailable();
        if (highlightMapper == null) {
            return Map.of();
        }
        List<CareerResumeHighlightDO> rows;
        try {
            rows = highlightMapper.selectList(Wrappers.<CareerResumeHighlightDO>lambdaQuery()
                    .eq(CareerResumeHighlightDO::getResumeId, resumeId)
                    .eq(CareerResumeHighlightDO::getDelFlag, 0)
                    .orderByAsc(CareerResumeHighlightDO::getOwnerType)
                    .orderByAsc(CareerResumeHighlightDO::getOwnerIndex)
                    .orderByAsc(CareerResumeHighlightDO::getItemIndex));
        } catch (Exception ex) {
            log.warn("Optional resume highlight lookup failed, using highlights_json/cv_json. resumeId={}", resumeId, ex);
            return Map.of();
        }
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<String, List<HighlightBO>> result = new HashMap<>();
        for (CareerResumeHighlightDO row : rows) {
            result.computeIfAbsent(highlightKey(row.getOwnerType(), row.getOwnerIndex()), ignored -> new java.util.ArrayList<>())
                    .add(fromHighlightRow(row));
        }
        return result;
    }

    private HighlightBO fromHighlightRow(CareerResumeHighlightDO row) {
        return HighlightBO.builder()
                .type(row.getType())
                .relatedId(row.getRelatedId())
                .highlight(row.getHighlight())
                .build();
    }

    private List<HighlightBO> detailsOrJsonHighlights(List<HighlightBO> detailHighlights, String highlightsJson) {
        if (detailHighlights != null && !detailHighlights.isEmpty()) {
            return detailHighlights;
        }
        return parseHighlights(highlightsJson);
    }

    private String highlightKey(String ownerType, Integer ownerIndex) {
        return ownerType + ":" + (ownerIndex == null ? 0 : ownerIndex);
    }

    private List<HighlightBO> parseHighlights(String highlightsJson) {
        if (highlightsJson == null || highlightsJson.isBlank()) {
            return List.of();
        }
        List<HighlightBO> highlights = JSON.parseArray(highlightsJson, HighlightBO.class);
        return highlights == null ? List.of() : highlights;
    }

    private void setAudit(com.hewei.hzyjy.xunzhi.common.database.BaseDO row, Date now) {
        row.setCreateTime(now);
        row.setUpdateTime(now);
        row.setDelFlag(0);
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
