package com.hewei.hzyjy.xunzhi.interview.flow.report;

import com.hewei.hzyjy.xunzhi.interview.api.io.resp.InterviewReviewFeedbackRespDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.RadarChartDTO;
import com.hewei.hzyjy.xunzhi.interview.application.InterviewSessionOwnershipService;
import com.hewei.hzyjy.xunzhi.interview.application.finalize.InterviewFinalizeLockService;
import com.hewei.hzyjy.xunzhi.interview.application.runtime.InterviewSessionRuntimeRehydrateService;
import com.hewei.hzyjy.xunzhi.interview.application.runtime.InterviewSessionRuntimeSnapshotService;
import com.hewei.hzyjy.xunzhi.interview.dao.entity.InterviewRecordDO;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionCacheService;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionService;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class InterviewReportReadFallbackTest {

    @Test
    void shouldReturnStoredFeedbackWithoutCallingAiWhenReadingLegacyReport() {
        InterviewReportAiReviewer reviewer = mock(InterviewReportAiReviewer.class);
        InterviewRecordServiceImpl service = new InterviewRecordServiceImpl(
                mock(InterviewQuestionCacheService.class),
                mock(InterviewSessionOwnershipService.class),
                mock(InterviewSessionService.class),
                mock(InterviewQuestionService.class),
                mock(InterviewFinalizeLockService.class),
                mock(InterviewSessionRuntimeSnapshotService.class),
                mock(InterviewSessionRuntimeRehydrateService.class),
                reviewer
        );
        Map<String, Object> snapshot = Map.of("reviewFeedback", Map.of(
                "overallComment", "Overall performance is below expectation.",
                "highlights", Collections.singletonList("Existing highlight"),
                "improvementTips", Collections.singletonList("Existing improvement"),
                "nextActions", Collections.singletonList("Existing action")
        ));

        InterviewReviewFeedbackRespDTO feedback = ReflectionTestUtils.invokeMethod(
                service,
                "resolveReviewFeedback",
                "session-1",
                "后端开发",
                snapshot,
                Collections.emptyList(),
                new RadarChartDTO(),
                new InterviewRecordDO()
        );

        assertEquals("Overall performance is below expectation.", feedback.getOverallComment());
        verifyNoInteractions(reviewer);
    }
}
