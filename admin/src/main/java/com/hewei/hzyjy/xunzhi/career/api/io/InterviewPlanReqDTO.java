package com.hewei.hzyjy.xunzhi.career.api.io;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewPlanReqDTO {
    @NotBlank
    private String sessionId;

    @NotNull
    private Long resumeId;

    @NotBlank
    @Size(max = 12000)
    private String jobDescription;
}
