package com.hewei.hzyjy.xunzhi.career.api.io;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class JobMatchReqDTO {
    @NotBlank
    @Size(max = 12000)
    private String jobDescription;

    private Integer limit = 3;

    /** Only the resume versions explicitly selected by the current user may be matched. */
    @NotEmpty
    private List<@NotNull Long> resumeIds;

    /** Null uses the server default; true/false overrides RAG for this request only. */
    private Boolean ragEnabled;
}
