package com.hewei.hzyjy.xunzhi.career.api.io;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InterviewReflectReqDTO {
    private String sessionId;
    @NotNull
    private Long resumeId;
    @NotBlank
    private String currentQuestion;
    @NotBlank
    private String userAnswer;
}
