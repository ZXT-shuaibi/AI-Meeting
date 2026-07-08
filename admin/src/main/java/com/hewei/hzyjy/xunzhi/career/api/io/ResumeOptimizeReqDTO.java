package com.hewei.hzyjy.xunzhi.career.api.io;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResumeOptimizeReqDTO {
    @NotBlank
    @Size(max = 12000)
    private String jobDescription;
}
