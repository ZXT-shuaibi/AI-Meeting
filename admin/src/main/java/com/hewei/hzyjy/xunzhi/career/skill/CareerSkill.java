package com.hewei.hzyjy.xunzhi.career.skill;

import java.util.Map;

public record CareerSkill(
        String name,
        String description,
        String body,
        Map<String, String> references
) {
}
