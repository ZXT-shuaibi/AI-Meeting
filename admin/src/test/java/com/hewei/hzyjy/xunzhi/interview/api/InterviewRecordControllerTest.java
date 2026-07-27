package com.hewei.hzyjy.xunzhi.interview.api;

import com.hewei.hzyjy.xunzhi.common.convention.context.UserContext;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import com.hewei.hzyjy.xunzhi.common.convention.result.Result;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewRecordService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InterviewRecordControllerTest {

    @Test
    void shouldTreatExistingFinalizeWorkAsAnIdempotentSuccess() {
        InterviewRecordService recordService = mock(InterviewRecordService.class);
        InterviewRecordController controller = new InterviewRecordController(recordService);
        UserContext currentUser = new UserContext(1001L, "tester");
        doThrow(new ClientException("finalize is processing, please retry"))
                .when(recordService)
                .saveInterviewRecordFromRedis("session-finalizing", 1001L);

        Result<Void> response = assertDoesNotThrow(() ->
                controller.saveInterviewRecordFromRedis("session-finalizing", currentUser));

        assertTrue(response.isSuccess());
        verify(recordService).saveInterviewRecordFromRedis("session-finalizing", 1001L);
    }
}
