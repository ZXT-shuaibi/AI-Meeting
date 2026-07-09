package com.hewei.hzyjy.xunzhi.career.skill;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ClasspathCareerSkillRegistry implements CareerSkillRegistry {

    private static final String BASE_PATH = "career-skills/";
    private final Map<String, CareerSkill> skills;

    public ClasspathCareerSkillRegistry(Map<String, CareerSkill> skills) {
        this.skills = Map.copyOf(skills);
    }

    public static ClasspathCareerSkillRegistry withBuiltIns() {
        Map<String, CareerSkill> loaded = new LinkedHashMap<>();
        loadSkill(loaded, "cv-reviewer", List.of());
        loadSkill(loaded, "cv-tailor", List.of());
        loadSkill(loaded, "jd-alignment", List.of("jd-template.md"));
        loadSkill(loaded, "question-probing", List.of("probing-strategies.md"));
        return new ClasspathCareerSkillRegistry(loaded);
    }

    @Override
    public Optional<CareerSkill> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(skills.get(name));
    }

    @Override
    public String promptSection(String name) {
        return find(name)
                .map(this::formatPromptSection)
                .orElse("");
    }

    private String formatPromptSection(CareerSkill skill) {
        StringBuilder builder = new StringBuilder();
        builder.append("Runtime Skill: ").append(skill.name()).append('\n');
        if (!safe(skill.description()).isBlank()) {
            builder.append("Description: ").append(skill.description()).append('\n');
        }
        builder.append(skill.body().strip()).append('\n');
        skill.references().forEach((fileName, content) -> builder
                .append("\nReference: ").append(fileName).append('\n')
                .append(content.strip()).append('\n'));
        return builder.toString();
    }

    private static void loadSkill(Map<String, CareerSkill> loaded, String name, List<String> references) {
        Optional<String> body = read(BASE_PATH + name + "/SKILL.md");
        if (body.isEmpty()) {
            return;
        }
        Map<String, String> referenceContents = new LinkedHashMap<>();
        for (String reference : references) {
            read(BASE_PATH + name + "/references/" + reference)
                    .ifPresent(content -> referenceContents.put(reference, content));
        }
        loaded.put(name, new CareerSkill(name, parseDescription(body.get()), body.get(), referenceContents));
    }

    private static Optional<String> read(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return Optional.empty();
        }
        try {
            return Optional.of(resource.getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private static String parseDescription(String body) {
        if (body == null || !body.startsWith("---")) {
            return "";
        }
        int end = body.indexOf("---", 3);
        if (end < 0) {
            return "";
        }
        String frontMatter = body.substring(3, end);
        for (String line : frontMatter.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("description:")) {
                return trimmed.substring("description:".length()).trim();
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
