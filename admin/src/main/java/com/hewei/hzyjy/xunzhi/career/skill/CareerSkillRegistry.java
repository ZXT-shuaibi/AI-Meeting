package com.hewei.hzyjy.xunzhi.career.skill;

import java.util.Optional;

public interface CareerSkillRegistry {

    Optional<CareerSkill> find(String name);

    String promptSection(String name);

    static CareerSkillRegistry disabled() {
        return new CareerSkillRegistry() {
            @Override
            public Optional<CareerSkill> find(String name) {
                return Optional.empty();
            }

            @Override
            public String promptSection(String name) {
                return "";
            }
        };
    }
}
