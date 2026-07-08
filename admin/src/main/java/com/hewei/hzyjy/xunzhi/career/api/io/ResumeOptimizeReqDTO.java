package com.hewei.hzyjy.xunzhi.career.api.io;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResumeOptimizeReqDTO {
    @NotBlank
    private String jobDescription;
}
