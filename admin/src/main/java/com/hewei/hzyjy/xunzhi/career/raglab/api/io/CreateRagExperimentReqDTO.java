package com.hewei.hzyjy.xunzhi.career.raglab.api.io;

import com.hewei.hzyjy.xunzhi.career.raglab.model.RagExperimentRuntimeOptions;
import com.hewei.hzyjy.xunzhi.career.raglab.model.RagRelevanceRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 创建并异步运行一次可复现 RAG 实验的请求。 */
@Data
public class CreateRagExperimentReqDTO {
    @NotBlank(message = "实验名称不能为空") @Size(max = 120)
    private String name;
    @NotNull(message = "必须选择测试集")
    private Long datasetId;
    @NotBlank(message = "岗位描述不能为空") @Size(max = 10000)
    private String jobDescription;
    @NotNull(message = "实验运行配置不能为空")
    private RagExperimentRuntimeOptions runtimeOptions;
    /** 首次匹配即生效；前端通过拖动顺序表达岗位、方向规则的优先级。 */
    @NotEmpty(message = "至少配置一条标签判定规则")
    private List<@Valid RagRelevanceRule> relevanceRules;
    @Size(max = 120)
    private String comparisonGroup;
}
