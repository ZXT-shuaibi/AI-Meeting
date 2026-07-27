package com.hewei.hzyjy.xunzhi.career.harness.api.io;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 创建 RAG 评测基线时的最小输入；真实配置和指标由后端从已完成实验快照复制。 */
@Data
public class CreateAgentEvaluationBaselineReqDTO {
    @NotBlank(message = "评测基线名称不能为空")
    private String name;
    @NotNull(message = "必须选择一条已完成的 RAG 实验")
    private Long experimentId;
}
