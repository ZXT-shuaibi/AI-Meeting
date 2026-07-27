package com.hewei.hzyjy.xunzhi.career.resume.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResumeApplicationServiceConstructorTest {

    @Test
    void marksFullDependencyConstructorForSpringInjectionWhenLegacyConstructorExists() {
        var constructor = Arrays.stream(ResumeApplicationService.class.getDeclaredConstructors())
                .filter(item -> item.getParameterCount() == 15)
                .findFirst()
                .orElseThrow();

        assertNotNull(constructor.getAnnotation(Autowired.class));
    }
}
