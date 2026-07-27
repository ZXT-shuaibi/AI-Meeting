package com.hewei.hzyjy.xunzhi.career.raglab.api.io;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 编辑测试集展示信息；候选简历由独立 items 接口维护，避免误改历史实验快照。 */
@Data
public class UpdateRagDatasetReqDTO {
    @NotBlank(message = "测试集名称不能为空")
    @Size(max = 120)
    private String name;

    @Size(max = 1000)
    private String description;
}
