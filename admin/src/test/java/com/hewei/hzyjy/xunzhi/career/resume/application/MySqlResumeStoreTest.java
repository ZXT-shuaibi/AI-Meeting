package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeContactDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeEducationDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeExperienceDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeCertificateDO;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MySqlResumeStoreTest {

    @Test
    void savePersistsStructuredResumeDetailsIntoDedicatedTables() {
        CareerResumeMapper resumeMapper = mock(CareerResumeMapper.class);
        CareerResumeContactMapper contactMapper = mock(CareerResumeContactMapper.class);
        CareerResumeEducationMapper educationMapper = mock(CareerResumeEducationMapper.class);
        CareerResumeExperienceMapper experienceMapper = mock(CareerResumeExperienceMapper.class);
        CareerResumeProjectMapper projectMapper = mock(CareerResumeProjectMapper.class);
        CareerResumeSkillMapper skillMapper = mock(CareerResumeSkillMapper.class);
        when(resumeMapper.insert(any(CareerResumeDO.class))).thenAnswer(invocation -> {
            CareerResumeDO row = invocation.getArgument(0);
            row.setId(21L);
            return 1;
        });
        MySqlResumeStore store = store(resumeMapper, contactMapper, educationMapper, experienceMapper, projectMapper, skillMapper);

        CvBO saved = store.save(structuredCv(null));

        assertEquals(21L, saved.getId());
        ArgumentCaptor<CareerResumeContactDO> contact = ArgumentCaptor.forClass(CareerResumeContactDO.class);
        ArgumentCaptor<CareerResumeEducationDO> education = ArgumentCaptor.forClass(CareerResumeEducationDO.class);
        ArgumentCaptor<CareerResumeExperienceDO> experience = ArgumentCaptor.forClass(CareerResumeExperienceDO.class);
        ArgumentCaptor<CareerResumeProjectDO> project = ArgumentCaptor.forClass(CareerResumeProjectDO.class);
        ArgumentCaptor<CareerResumeSkillDO> skill = ArgumentCaptor.forClass(CareerResumeSkillDO.class);
        verify(contactMapper).insert(contact.capture());
        verify(educationMapper).insert(education.capture());
        verify(experienceMapper).insert(experience.capture());
        verify(projectMapper).insert(project.capture());
        verify(skillMapper).insert(skill.capture());
        assertEquals(21L, contact.getValue().getResumeId());
        assertEquals("dev@example.com", contact.getValue().getEmail());
        assertEquals("Zhejiang University", education.getValue().getSchool());
        assertEquals(0, education.getValue().getItemIndex());
        assertEquals("Backend Engineer", experience.getValue().getRole());
        assertTrue(experience.getValue().getHighlightsJson().contains("High concurrency"));
        assertEquals("AI Interview Platform", project.getValue().getName());
        assertEquals("LangChain4j", skill.getValue().getName());
    }

    @Test
    void savePersistsJobSparkExtendedResumeDetailsIntoDedicatedTables() {
        CareerResumeMapper resumeMapper = mock(CareerResumeMapper.class);
        CareerResumeSocialLinkMapper socialLinkMapper = mock(CareerResumeSocialLinkMapper.class);
        CareerResumeCertificateMapper certificateMapper = mock(CareerResumeCertificateMapper.class);
        CareerResumeFormatMetaMapper formatMetaMapper = mock(CareerResumeFormatMetaMapper.class);
        CareerResumeLocaleConfigMapper localeConfigMapper = mock(CareerResumeLocaleConfigMapper.class);
        CareerResumeHighlightMapper highlightMapper = mock(CareerResumeHighlightMapper.class);
        when(resumeMapper.insert(any(CareerResumeDO.class))).thenAnswer(invocation -> {
            CareerResumeDO row = invocation.getArgument(0);
            row.setId(22L);
            return 1;
        });
        MySqlResumeStore store = store(
                resumeMapper,
                mock(CareerResumeContactMapper.class),
                mock(CareerResumeEducationMapper.class),
                mock(CareerResumeExperienceMapper.class),
                mock(CareerResumeProjectMapper.class),
                mock(CareerResumeSkillMapper.class),
                socialLinkMapper,
                certificateMapper,
                formatMetaMapper,
                localeConfigMapper,
                highlightMapper
        );

        CvBO saved = store.save(jobSparkExtendedCv(null));

        assertEquals(22L, saved.getId());
        ArgumentCaptor<CareerResumeSocialLinkDO> socialLink = ArgumentCaptor.forClass(CareerResumeSocialLinkDO.class);
        ArgumentCaptor<CareerResumeCertificateDO> certificate = ArgumentCaptor.forClass(CareerResumeCertificateDO.class);
        ArgumentCaptor<CareerResumeFormatMetaDO> formatMeta = ArgumentCaptor.forClass(CareerResumeFormatMetaDO.class);
        ArgumentCaptor<CareerResumeLocaleConfigDO> localeConfig = ArgumentCaptor.forClass(CareerResumeLocaleConfigDO.class);
        ArgumentCaptor<CareerResumeHighlightDO> highlight = ArgumentCaptor.forClass(CareerResumeHighlightDO.class);
        verify(socialLinkMapper).insert(socialLink.capture());
        verify(certificateMapper).insert(certificate.capture());
        verify(formatMetaMapper).insert(formatMeta.capture());
        verify(localeConfigMapper).insert(localeConfig.capture());
        verify(highlightMapper, times(3)).insert(highlight.capture());
        assertEquals("GitHub", socialLink.getValue().getName());
        assertEquals("ACP", certificate.getValue().getName());
        assertEquals("left", formatMeta.getValue().getAlignment());
        assertEquals("zh-CN", localeConfig.getValue().getLocale());
        assertTrue(highlight.getAllValues().stream().anyMatch(row ->
                "experience".equals(row.getOwnerType()) && "exp-impact".equals(row.getRelatedId())));
        assertTrue(highlight.getAllValues().stream().anyMatch(row ->
                "project".equals(row.getOwnerType()) && "project-impact".equals(row.getRelatedId())));
        assertTrue(highlight.getAllValues().stream().anyMatch(row ->
                "skill".equals(row.getOwnerType()) && "skill-impact".equals(row.getRelatedId())));
    }

    @Test
    void findByIdRebuildsStructuredResumeFromDedicatedTables() {
        CareerResumeMapper resumeMapper = mock(CareerResumeMapper.class);
        CareerResumeContactMapper contactMapper = mock(CareerResumeContactMapper.class);
        CareerResumeEducationMapper educationMapper = mock(CareerResumeEducationMapper.class);
        CareerResumeExperienceMapper experienceMapper = mock(CareerResumeExperienceMapper.class);
        CareerResumeProjectMapper projectMapper = mock(CareerResumeProjectMapper.class);
        CareerResumeSkillMapper skillMapper = mock(CareerResumeSkillMapper.class);
        CareerResumeDO row = new CareerResumeDO();
        row.setId(21L);
        row.setUserId(7L);
        row.setCvType("upload");
        row.setName("json-only");
        row.setCvJson("{\"name\":\"json-only\",\"educations\":[],\"experiences\":[],\"projects\":[],\"skills\":[]}");
        when(resumeMapper.selectOne(any())).thenReturn(row);
        when(contactMapper.selectOne(any())).thenReturn(contactRow());
        when(educationMapper.selectList(any())).thenReturn(List.of(educationRow()));
        when(experienceMapper.selectList(any())).thenReturn(List.of(experienceRow()));
        when(projectMapper.selectList(any())).thenReturn(List.of(projectRow()));
        when(skillMapper.selectList(any())).thenReturn(List.of(skillRow()));
        MySqlResumeStore store = store(resumeMapper, contactMapper, educationMapper, experienceMapper, projectMapper, skillMapper);

        Optional<CvBO> loaded = store.findById(21L);

        assertTrue(loaded.isPresent());
        CvBO cv = loaded.get();
        assertEquals("dev@example.com", cv.getContact().getEmail());
        assertEquals("Zhejiang University", cv.getEducations().get(0).getSchool());
        assertEquals("Backend Engineer", cv.getExperiences().get(0).getRole());
        assertEquals("High concurrency", cv.getExperiences().get(0).getHighlights().get(0).getHighlight());
        assertEquals("AI Interview Platform", cv.getProjects().get(0).getName());
        assertEquals("LangChain4j", cv.getSkills().get(0).getName());
    }

    @Test
    void findByIdRebuildsJobSparkExtendedDetailsFromDedicatedTables() {
        CareerResumeMapper resumeMapper = mock(CareerResumeMapper.class);
        CareerResumeSocialLinkMapper socialLinkMapper = mock(CareerResumeSocialLinkMapper.class);
        CareerResumeCertificateMapper certificateMapper = mock(CareerResumeCertificateMapper.class);
        CareerResumeFormatMetaMapper formatMetaMapper = mock(CareerResumeFormatMetaMapper.class);
        CareerResumeLocaleConfigMapper localeConfigMapper = mock(CareerResumeLocaleConfigMapper.class);
        CareerResumeHighlightMapper highlightMapper = mock(CareerResumeHighlightMapper.class);
        CareerResumeDO row = new CareerResumeDO();
        row.setId(22L);
        row.setUserId(7L);
        row.setCvType("upload");
        row.setName("json-only");
        row.setCvJson("{\"name\":\"json-only\",\"socialLinks\":[],\"certificates\":[],\"educations\":[],\"experiences\":[],\"projects\":[],\"skills\":[]}");
        when(resumeMapper.selectOne(any())).thenReturn(row);
        when(socialLinkMapper.selectList(any())).thenReturn(List.of(socialLinkRow()));
        when(certificateMapper.selectList(any())).thenReturn(List.of(certificateRow()));
        when(formatMetaMapper.selectOne(any())).thenReturn(formatMetaRow());
        when(localeConfigMapper.selectOne(any())).thenReturn(localeConfigRow());
        when(highlightMapper.selectList(any())).thenReturn(List.of(
                highlightRow("experience", 0, "impact", "exp-impact", "Cut p95 latency"),
                highlightRow("project", 0, "impact", "project-impact", "Agentic loop"),
                highlightRow("skill", 0, "impact", "skill-impact", "LangChain4j RAG")
        ));
        MySqlResumeStore store = store(
                resumeMapper,
                null,
                null,
                experienceMapperWithRows(),
                projectMapperWithRows(),
                skillMapperWithRows(),
                socialLinkMapper,
                certificateMapper,
                formatMetaMapper,
                localeConfigMapper,
                highlightMapper
        );

        Optional<CvBO> loaded = store.findById(22L);

        assertTrue(loaded.isPresent());
        CvBO cv = loaded.get();
        assertEquals("GitHub", cv.getSocialLinks().get(0).getName());
        assertEquals("ACP", cv.getCertificates().get(0).getName());
        assertEquals("left", cv.getMeta().getAlignment());
        assertEquals("zh-CN", cv.getMeta().getLocaleConfig().getLocale());
        assertEquals("Cut p95 latency", cv.getExperiences().get(0).getHighlights().get(0).getHighlight());
        assertEquals("Agentic loop", cv.getProjects().get(0).getHighlights().get(0).getHighlight());
        assertEquals("LangChain4j RAG", cv.getSkills().get(0).getHighlights().get(0).getHighlight());
    }

    @Test
    void findByIdKeepsCvJsonFallbackWhenExtendedDetailTablesAreUnavailable() {
        CareerResumeMapper resumeMapper = mock(CareerResumeMapper.class);
        CareerResumeSocialLinkMapper socialLinkMapper = mock(CareerResumeSocialLinkMapper.class);
        CareerResumeDO row = new CareerResumeDO();
        row.setId(23L);
        row.setUserId(7L);
        row.setCvType("upload");
        row.setName("json-backed");
        row.setCvJson("""
                {"name":"json-backed","socialLinks":[{"name":"GitHub","url":"https://github.com/json"}],"certificates":[],"educations":[],"experiences":[],"projects":[],"skills":[]}
                """);
        when(resumeMapper.selectOne(any())).thenReturn(row);
        when(socialLinkMapper.selectList(any())).thenThrow(new IllegalStateException("missing optional table"));
        MySqlResumeStore store = store(
                resumeMapper,
                null,
                null,
                null,
                null,
                null,
                socialLinkMapper,
                null,
                null,
                null,
                null
        );

        Optional<CvBO> loaded = store.findById(23L);

        assertTrue(loaded.isPresent());
        assertEquals("GitHub", loaded.get().getSocialLinks().get(0).getName());
    }

    private MySqlResumeStore store(
            CareerResumeMapper resumeMapper,
            CareerResumeContactMapper contactMapper,
            CareerResumeEducationMapper educationMapper,
            CareerResumeExperienceMapper experienceMapper,
            CareerResumeProjectMapper projectMapper,
            CareerResumeSkillMapper skillMapper) {
        return store(
                resumeMapper,
                contactMapper,
                educationMapper,
                experienceMapper,
                projectMapper,
                skillMapper,
                null,
                null,
                null,
                null,
                null
        );
    }

    private MySqlResumeStore store(
            CareerResumeMapper resumeMapper,
            CareerResumeContactMapper contactMapper,
            CareerResumeEducationMapper educationMapper,
            CareerResumeExperienceMapper experienceMapper,
            CareerResumeProjectMapper projectMapper,
            CareerResumeSkillMapper skillMapper,
            CareerResumeSocialLinkMapper socialLinkMapper,
            CareerResumeCertificateMapper certificateMapper,
            CareerResumeFormatMetaMapper formatMetaMapper,
            CareerResumeLocaleConfigMapper localeConfigMapper,
            CareerResumeHighlightMapper highlightMapper) {
        return new MySqlResumeStore(
                provider(resumeMapper),
                provider(contactMapper),
                provider(educationMapper),
                provider(experienceMapper),
                provider(projectMapper),
                provider(skillMapper),
                provider(socialLinkMapper),
                provider(certificateMapper),
                provider(formatMetaMapper),
                provider(localeConfigMapper),
                provider(highlightMapper),
                new InMemoryResumeStore()
        );
    }

    private CvBO structuredCv(Long id) {
        return CvBO.builder()
                .id(id)
                .userId(7L)
                .cvType("upload")
                .name("candidate")
                .contact(ContactBO.builder().email("dev@example.com").phone("18800000000").location("Hangzhou").website("https://example.com").build())
                .educations(List.of(EducationBO.builder()
                        .school("Zhejiang University")
                        .major("Computer Science")
                        .degree("Bachelor")
                        .startDate(LocalDate.of(2020, 9, 1))
                        .endDate(LocalDate.of(2024, 6, 30))
                        .description("GPA 3.8")
                        .build()))
                .experiences(List.of(ExperienceBO.builder()
                        .company("Acme")
                        .industry("AI")
                        .role("Backend Engineer")
                        .startDate(LocalDate.of(2024, 7, 1))
                        .description("Built resume RAG")
                        .highlights(List.of(HighlightBO.builder().type("impact").highlight("High concurrency").build()))
                        .build()))
                .projects(List.of(ProjectBO.builder()
                        .name("AI Interview Platform")
                        .role("Owner")
                        .description("Plan Execute Reflect")
                        .highlights(List.of(HighlightBO.builder().type("architecture").highlight("Agent loop").build()))
                        .build()))
                .skills(List.of(SkillBO.builder()
                        .category("AI")
                        .name("LangChain4j")
                        .level("advanced")
                        .highlights(List.of(HighlightBO.builder().type("project").highlight("RAG").build()))
                        .build()))
                .build();
    }

    private CvBO jobSparkExtendedCv(Long id) {
        CvBO base = structuredCv(id);
        return base.toBuilder()
                .socialLinks(List.of(SocialLinkBO.builder().name("GitHub").url("https://github.com/acme").build()))
                .certificates(List.of(CertificateBO.builder()
                        .name("ACP")
                        .issuer("Alibaba Cloud")
                        .issueDate(LocalDate.of(2025, 1, 10))
                        .description("Cloud native certification")
                        .build()))
                .experiences(List.of(ExperienceBO.builder()
                        .company("Acme")
                        .industry("AI")
                        .role("Backend Engineer")
                        .startDate(LocalDate.of(2024, 7, 1))
                        .description("Built resume RAG")
                        .highlights(List.of(HighlightBO.builder().type("impact").relatedId("exp-impact").highlight("Cut p95 latency").build()))
                        .build()))
                .projects(List.of(ProjectBO.builder()
                        .name("AI Interview Platform")
                        .role("Owner")
                        .description("Plan Execute Reflect")
                        .highlights(List.of(HighlightBO.builder().type("impact").relatedId("project-impact").highlight("Agentic loop").build()))
                        .build()))
                .skills(List.of(SkillBO.builder()
                        .category("AI")
                        .name("LangChain4j")
                        .level("advanced")
                        .highlights(List.of(HighlightBO.builder().type("impact").relatedId("skill-impact").highlight("LangChain4j RAG").build()))
                        .build()))
                .meta(FormatMetaBO.builder()
                        .alignment("left")
                        .lineSpacing(1.25)
                        .fontFamily("Noto Sans SC")
                        .datePattern("yyyy-MM")
                        .hyperlinkStyle("underline")
                        .showAvatar(false)
                        .showSocial(true)
                        .twoColumnLayout(true)
                        .localeConfig(LocaleConfigBO.builder()
                                .locale("zh-CN")
                                .datePattern("yyyy-MM")
                                .sectionLabels("\u9879\u76ee\u7ecf\u5386,\u4e13\u4e1a\u6280\u80fd")
                                .build())
                        .build())
                .build();
    }

    private CareerResumeContactDO contactRow() {
        CareerResumeContactDO row = new CareerResumeContactDO();
        row.setResumeId(21L);
        row.setEmail("dev@example.com");
        row.setPhone("18800000000");
        row.setLocation("Hangzhou");
        row.setWebsite("https://example.com");
        return row;
    }

    private CareerResumeEducationDO educationRow() {
        CareerResumeEducationDO row = new CareerResumeEducationDO();
        row.setResumeId(21L);
        row.setItemIndex(0);
        row.setSchool("Zhejiang University");
        row.setMajor("Computer Science");
        row.setDegree("Bachelor");
        row.setStartDate(LocalDate.of(2020, 9, 1));
        row.setEndDate(LocalDate.of(2024, 6, 30));
        row.setDescription("GPA 3.8");
        return row;
    }

    private CareerResumeExperienceDO experienceRow() {
        CareerResumeExperienceDO row = new CareerResumeExperienceDO();
        row.setResumeId(21L);
        row.setItemIndex(0);
        row.setCompany("Acme");
        row.setIndustry("AI");
        row.setRole("Backend Engineer");
        row.setDescription("Built resume RAG");
        row.setHighlightsJson("[{\"type\":\"impact\",\"highlight\":\"High concurrency\"}]");
        return row;
    }

    private CareerResumeProjectDO projectRow() {
        CareerResumeProjectDO row = new CareerResumeProjectDO();
        row.setResumeId(21L);
        row.setItemIndex(0);
        row.setName("AI Interview Platform");
        row.setRole("Owner");
        row.setDescription("Plan Execute Reflect");
        row.setHighlightsJson("[{\"type\":\"architecture\",\"highlight\":\"Agent loop\"}]");
        return row;
    }

    private CareerResumeSkillDO skillRow() {
        CareerResumeSkillDO row = new CareerResumeSkillDO();
        row.setResumeId(21L);
        row.setItemIndex(0);
        row.setCategory("AI");
        row.setName("LangChain4j");
        row.setLevel("advanced");
        row.setHighlightsJson("[{\"type\":\"project\",\"highlight\":\"RAG\"}]");
        return row;
    }

    private CareerResumeExperienceMapper experienceMapperWithRows() {
        CareerResumeExperienceMapper mapper = mock(CareerResumeExperienceMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(experienceRow()));
        return mapper;
    }

    private CareerResumeProjectMapper projectMapperWithRows() {
        CareerResumeProjectMapper mapper = mock(CareerResumeProjectMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(projectRow()));
        return mapper;
    }

    private CareerResumeSkillMapper skillMapperWithRows() {
        CareerResumeSkillMapper mapper = mock(CareerResumeSkillMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(skillRow()));
        return mapper;
    }

    private CareerResumeSocialLinkDO socialLinkRow() {
        CareerResumeSocialLinkDO row = new CareerResumeSocialLinkDO();
        row.setResumeId(22L);
        row.setItemIndex(0);
        row.setName("GitHub");
        row.setUrl("https://github.com/acme");
        return row;
    }

    private CareerResumeCertificateDO certificateRow() {
        CareerResumeCertificateDO row = new CareerResumeCertificateDO();
        row.setResumeId(22L);
        row.setItemIndex(0);
        row.setName("ACP");
        row.setIssuer("Alibaba Cloud");
        row.setIssueDate(LocalDate.of(2025, 1, 10));
        row.setDescription("Cloud native certification");
        return row;
    }

    private CareerResumeFormatMetaDO formatMetaRow() {
        CareerResumeFormatMetaDO row = new CareerResumeFormatMetaDO();
        row.setResumeId(22L);
        row.setAlignment("left");
        row.setLineSpacing(1.25);
        row.setFontFamily("Noto Sans SC");
        row.setDatePattern("yyyy-MM");
        row.setHyperlinkStyle("underline");
        row.setShowAvatar(false);
        row.setShowSocial(true);
        row.setTwoColumnLayout(true);
        return row;
    }

    private CareerResumeLocaleConfigDO localeConfigRow() {
        CareerResumeLocaleConfigDO row = new CareerResumeLocaleConfigDO();
        row.setResumeId(22L);
        row.setLocale("zh-CN");
        row.setDatePattern("yyyy-MM");
        row.setSectionLabels("\u9879\u76ee\u7ecf\u5386,\u4e13\u4e1a\u6280\u80fd");
        return row;
    }

    private CareerResumeHighlightDO highlightRow(String ownerType, int ownerIndex, String type, String relatedId, String value) {
        CareerResumeHighlightDO row = new CareerResumeHighlightDO();
        row.setResumeId(22L);
        row.setOwnerType(ownerType);
        row.setOwnerIndex(ownerIndex);
        row.setItemIndex(0);
        row.setType(type);
        row.setRelatedId(relatedId);
        row.setHighlight(value);
        return row;
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }
}
