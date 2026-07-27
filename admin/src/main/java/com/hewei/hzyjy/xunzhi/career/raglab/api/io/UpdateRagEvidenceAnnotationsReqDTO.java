package com.hewei.hzyjy.xunzhi.career.raglab.api.io;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 对一次实验返回的具体证据片段进行人工核验。
 *
 * <p>该标注只属于当前实验快照，不会改变简历的基础标签或其他实验记录。</p>
 */
@Data
public class UpdateRagEvidenceAnnotationsReqDTO {

    @NotEmpty
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull
        private Long resumeId;
        @NotNull
        private Integer evidenceIndex;
        @NotNull
        private Boolean hit;
    }
}
