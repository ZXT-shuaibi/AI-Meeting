package com.hewei.hzyjy.xunzhi.career.api.io;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewReflectReqDTO {
    private String sessionId;

    @NotNull
    private Long resumeId;

    @NotBlank
    @Size(max = 4000)
    private String currentQuestion;

    @NotBlank
    @Size(max = 12000)
    private String userAnswer;
}
