package com.hewei.hzyjy.xunzhi.career.raglab.api.io;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 更新测试集参与检索的候选简历；历史实验结果仍保留原有快照。 */
@Data
public class UpdateRagDatasetItemsReqDTO {
    @NotEmpty(message = "测试集至少需要保留一份已解析简历")
    private List<Long> resumeIds;
}
