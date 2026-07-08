package com.hewei.hzyjy.xunzhi.career.api.io;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JobMatchReqDTO {
    @NotBlank
    @Size(max = 12000)
    private String jobDescription;

    @Max(20)
    private Integer limit = 3;
}
