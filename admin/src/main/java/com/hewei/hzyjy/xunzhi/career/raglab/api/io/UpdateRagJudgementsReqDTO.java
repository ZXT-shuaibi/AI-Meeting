package com.hewei.hzyjy.xunzhi.career.raglab.api.io;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/** 对少量特殊简历覆写冻结真值；该操作不会改动跨实验可复用的岗位/方向标签。 */
@Data
public class UpdateRagJudgementsReqDTO {
    @NotNull
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull private Long resumeId;
        @NotNull private Integer score;
        private String reason;
    }
}
