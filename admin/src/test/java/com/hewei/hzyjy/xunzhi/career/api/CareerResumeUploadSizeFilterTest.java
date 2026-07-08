package com.hewei.hzyjy.xunzhi.career.api;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CareerResumeUploadSizeFilterTest {

    @Test
    void rejectsOversizedResumeUploadBeforeControllerReadsMultipart() throws Exception {
        CareerResumeUploadSizeFilter filter = new CareerResumeUploadSizeFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/xunzhi/v1/resumes/upload");
        request.setContent(new byte[(int) CareerResumeUploadSizeFilter.MAX_RESUME_MULTIPART_REQUEST_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }
}