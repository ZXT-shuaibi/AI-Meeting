package com.hewei.hzyjy.xunzhi.career.config;

import com.hewei.hzyjy.xunzhi.career.agent.cv.CvReview;
import com.hewei.hzyjy.xunzhi.career.agent.cv.CvReviewer;
import com.hewei.hzyjy.xunzhi.career.agent.cv.ScoredCvTailor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangChain4jRuntimeConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LangChain4jRuntimeConfiguration.class)
            .withBean("aiCvReviewer", CvReviewer.class, () -> (cv, jobDescription, referenceTemplates) ->
                    new CvReview(0.82, "real resume reviewer"))
            .withBean("aiScoredCvTailor", ScoredCvTailor.class, () -> (cv, review, referenceTemplates) -> cv);

    @Test
    void keepsResumeLocalFallbackDisabledToAvoidPretendingLangChain4jSuccess() {
        assertFalse(LangChain4jRuntimeConfiguration.resumeFallbackAgentsEnabled());
    }

    @Test
    void keepsInterviewLocalFallbackEnabledForNonResumeFlows() {
        assertTrue(LangChain4jRuntimeConfiguration.interviewFallbackAgentsEnabled());
    }

    @Test
    void doesNotCreateResumeLocalFallbackBeansByDefaultWhenRealResumeAgentsExist() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("aiCvReviewer");
            assertThat(context).hasBean("aiScoredCvTailor");
            assertThat(context).doesNotHaveBean("CvReviewer");
            assertThat(context).doesNotHaveBean("ScoredCvTailor");
        });
    }
}
