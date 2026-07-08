package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeContactDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeEducationDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeExperienceDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeProjectDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.entity.CareerResumeSkillDO;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeContactMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeEducationMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeExperienceMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeProjectMapper;
import com.hewei.hzyjy.xunzhi.career.resume.dao.mapper.CareerResumeSkillMapper;
import com.hewei.hzyjy.xunzhi.career.resume.model.ContactBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.EducationBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.ExperienceBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.HighlightBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.ProjectBO;
import com.hewei.hzyjy.xunzhi.career.resume.model.SkillBO;
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

    private MySqlResumeStore store(
            CareerResumeMapper resumeMapper,
            CareerResumeContactMapper contactMapper,
            CareerResumeEducationMapper educationMapper,
            CareerResumeExperienceMapper experienceMapper,
            CareerResumeProjectMapper projectMapper,
            CareerResumeSkillMapper skillMapper) {
        return new MySqlResumeStore(
                provider(resumeMapper),
                provider(contactMapper),
                provider(educationMapper),
                provider(experienceMapper),
                provider(projectMapper),
                provider(skillMapper),
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