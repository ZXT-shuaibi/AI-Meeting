package com.hewei.hzyjy.xunzhi.career.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CareerJobMatchPropertiesTest {

    @Test
    void defaultsToTwoHundredCandidatesAndBoundsTopKByConfiguredLimit() {
        CareerJobMatchProperties properties = new CareerJobMatchProperties();

        assertEquals(200, properties.maxCandidateResumes());
        assertEquals(200, properties.normalizeResultLimit(999));
        assertEquals(3, properties.normalizeResultLimit(0));
    }

    @Test
    void usesConfiguredCandidateLimitForLargeScaleExperiments() {
        CareerJobMatchProperties properties = new CareerJobMatchProperties();
        properties.setMaxCandidateResumes(500);

        assertEquals(500, properties.maxCandidateResumes());
        assertEquals(500, properties.normalizeResultLimit(999));
    }

    @Test
    void allowsMoreThanTheLegacyTwentyCandidateLimit() {
        CareerJobMatchProperties properties = new CareerJobMatchProperties();

        assertEquals(true, properties.allowsCandidateCount(21));
        assertEquals(false, properties.allowsCandidateCount(201));
    }
}
