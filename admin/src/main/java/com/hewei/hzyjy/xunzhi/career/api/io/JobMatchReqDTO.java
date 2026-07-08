package com.hewei.hzyjy.xunzhi.career.api.io;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JobMatchReqDTO {
    @NotBlank
    private String jobDescription;
    private Integer limit = 3;
}
