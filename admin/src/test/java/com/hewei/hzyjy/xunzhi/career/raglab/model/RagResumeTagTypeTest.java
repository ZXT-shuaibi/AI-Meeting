package com.hewei.hzyjy.xunzhi.career.raglab.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagResumeTagTypeTest {

    @Test
    void supportsReusableProjectSkillAndExperienceTags() {
        assertEquals("PROJECT", RagResumeTagType.PROJECT.getValue());
        assertEquals("SKILL", RagResumeTagType.SKILL.getValue());
        assertEquals("EXPERIENCE", RagResumeTagType.EXPERIENCE.getValue());
    }
}
