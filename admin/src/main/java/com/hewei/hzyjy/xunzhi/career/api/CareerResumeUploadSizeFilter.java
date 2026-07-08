package com.hewei.hzyjy.xunzhi.career.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CareerResumeUploadSizeFilter extends OncePerRequestFilter {

    static final long MAX_RESUME_MULTIPART_REQUEST_BYTES = 6L * 1024 * 1024;
    private static final String RESUME_UPLOAD_PATH = "/api/xunzhi/v1/resumes/upload";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isResumeUpload(request)) {
            long contentLength = request.getContentLengthLong();
            if (contentLength < 0) {
                response.sendError(HttpServletResponse.SC_LENGTH_REQUIRED, "Content-Length is required for resume upload");
                return;
            }
            if (contentLength > MAX_RESUME_MULTIPART_REQUEST_BYTES) {
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Resume upload request exceeds 6MB limit");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isResumeUpload(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI() != null
                && request.getRequestURI().endsWith(RESUME_UPLOAD_PATH);
    }
}