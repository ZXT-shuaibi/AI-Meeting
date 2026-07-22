package com.hewei.hzyjy.xunzhi.career.raglab.api.io;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 创建 RAG 测试集的请求。 */
@Data
public class CreateRagDatasetReqDTO {
    @NotBlank(message = "测试集名称不能为空") @Size(max = 120)
    private String name;
    @Size(max = 1000)
    private String description;
    @NotEmpty(message = "至少选择一份已解析简历")
    private List<Long> resumeIds;
}
