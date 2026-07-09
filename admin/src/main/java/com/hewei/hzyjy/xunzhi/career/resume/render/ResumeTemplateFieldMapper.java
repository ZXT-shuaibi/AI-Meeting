package com.hewei.hzyjy.xunzhi.career.resume.render;

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
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResumeTemplateFieldMapper {

    public Map<String, Object> toTemplateData(CvBO cv) {
        validateRequired(cv);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", fallback(cv.getName(), "未命名简历"));
        data.put("birthDate", cv.getBirthDate());
        data.put("title", text(cv.getTitle()));
        data.put("avatarUrl", text(cv.getAvatarUrl()));
        data.put("summary", text(cv.getSummary()));
        data.put("contact", contact(cv.getContact()));
        data.put("socialLinks", socialLinks(cv.getSocialLinks()));
        data.put("educations", educations(cv.getEducations()));
        data.put("experiences", experiences(cv.getExperiences()));
        data.put("projects", projects(cv.getProjects()));
        data.put("skills", skills(cv.getSkills()));
        data.put("certificates", certificates(cv.getCertificates()));
        data.put("meta", meta(cv.getMeta()));
        return data;
    }

    private void validateRequired(CvBO cv) {
        if (cv == null) {
            throw new ResumeRenderException("CvBO must not be null");
        }
    }

    private Map<String, Object> contact(ContactBO contact) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phone", "");
        result.put("email", "");
        result.put("wechat", "");
        result.put("location", "");
        result.put("website", "");
        if (contact == null) {
            return result;
        }
        result.put("phone", text(contact.getPhone()));
        result.put("email", text(contact.getEmail()));
        result.put("location", text(contact.getLocation()));
        result.put("website", text(contact.getWebsite()));
        return result;
    }

    private List<Map<String, Object>> socialLinks(List<SocialLinkBO> links) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (links == null) {
            return result;
        }
        for (SocialLinkBO link : links) {
            if (link == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", text(link.getName()));
            item.put("url", text(link.getUrl()));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> educations(List<EducationBO> educations) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (educations == null) {
            return result;
        }
        for (EducationBO education : educations) {
            if (education == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("school", text(education.getSchool()));
            item.put("major", text(education.getMajor()));
            item.put("degree", text(education.getDegree()));
            item.put("startDate", education.getStartDate());
            item.put("endDate", education.getEndDate());
            item.put("description", text(education.getDescription()));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> experiences(List<ExperienceBO> experiences) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (experiences == null) {
            return result;
        }
        for (ExperienceBO experience : experiences) {
            if (experience == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("company", text(experience.getCompany()));
            item.put("industry", text(experience.getIndustry()));
            item.put("role", text(experience.getRole()));
            item.put("startDate", experience.getStartDate());
            item.put("endDate", experience.getEndDate());
            item.put("description", text(experience.getDescription()));
            item.put("highlights", highlights(experience.getHighlights()));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> projects(List<ProjectBO> projects) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (projects == null) {
            return result;
        }
        for (ProjectBO project : projects) {
            if (project == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", text(project.getName()));
            item.put("role", text(project.getRole()));
            item.put("startDate", project.getStartDate());
            item.put("endDate", project.getEndDate());
            item.put("description", text(project.getDescription()));
            item.put("highlights", highlights(project.getHighlights()));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> skills(List<SkillBO> skills) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (skills == null) {
            return result;
        }
        for (SkillBO skill : skills) {
            if (skill == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", text(skill.getCategory()));
            item.put("name", text(skill.getName()));
            item.put("level", text(skill.getLevel()));
            item.put("highlights", highlights(skill.getHighlights()));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> certificates(List<CertificateBO> certificates) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (certificates == null) {
            return result;
        }
        for (CertificateBO certificate : certificates) {
            if (certificate == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", text(certificate.getName()));
            item.put("issuer", text(certificate.getIssuer()));
            item.put("date", certificate.getIssueDate());
            item.put("description", text(certificate.getDescription()));
            result.add(item);
        }
        return result;
    }

    private List<String> highlights(List<HighlightBO> highlights) {
        List<String> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(highlights)) {
            return result;
        }
        for (HighlightBO highlight : highlights) {
            if (highlight != null && StringUtils.hasText(highlight.getHighlight())) {
                result.add(highlight.getHighlight().strip());
            }
        }
        return result;
    }

    private Map<String, Object> meta(FormatMetaBO meta) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("alignment", "");
        result.put("lineSpacing", null);
        result.put("fontFamily", "");
        result.put("datePattern", "yyyy.MM");
        result.put("hyperlinkStyle", "");
        result.put("showAvatar", false);
        result.put("showSocial", true);
        result.put("twoColumnLayout", false);
        result.put("locale", Map.of("locale", "zh-CN", "datePattern", "yyyy.MM"));
        if (meta == null) {
            return result;
        }
        result.put("alignment", text(meta.getAlignment()));
        result.put("lineSpacing", meta.getLineSpacing());
        result.put("fontFamily", text(meta.getFontFamily()));
        result.put("datePattern", StringUtils.hasText(meta.getDatePattern()) ? meta.getDatePattern().strip() : "yyyy.MM");
        result.put("hyperlinkStyle", text(meta.getHyperlinkStyle()));
        result.put("showAvatar", Boolean.TRUE.equals(meta.getShowAvatar()));
        result.put("showSocial", meta.getShowSocial() == null || Boolean.TRUE.equals(meta.getShowSocial()));
        result.put("twoColumnLayout", Boolean.TRUE.equals(meta.getTwoColumnLayout()));
        result.put("locale", locale(meta.getLocaleConfig()));
        return result;
    }

    private Map<String, Object> locale(LocaleConfigBO locale) {
        if (locale == null) {
            return Map.of("locale", "zh-CN", "datePattern", "yyyy.MM");
        }
        return Map.of(
                "locale", StringUtils.hasText(locale.getLocale()) ? locale.getLocale().strip() : "zh-CN",
                "datePattern", StringUtils.hasText(locale.getDatePattern()) ? locale.getDatePattern().strip() : "yyyy.MM"
        );
    }

    private String text(String value) {
        return StringUtils.hasText(value) ? value.strip() : "";
    }

    private String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value.strip() : fallback;
    }
}
