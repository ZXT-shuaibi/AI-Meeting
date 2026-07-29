package com.hewei.hzyjy.xunzhi.career.config;

import com.hewei.hzyjy.xunzhi.career.security.JobDescriptionSafetyMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** JD 注入防护的部署参数。默认值保证不写配置时仍按 ENFORCE 安全执行。 */
@Data
@ConfigurationProperties(prefix = "xunzhi-agent.career.jd-safety")
public class CareerJobDescriptionSafetyProperties {
    private JobDescriptionSafetyMode mode = JobDescriptionSafetyMode.ENFORCE;
    private boolean llmStructuringEnabled = true;
    private boolean rejectOnHighRiskWithoutProfile = true;
    private int minMeaningfulFields = 2;
    /** 岗位画像中技能和职责的合计最小项数，避免仅凭岗位名称或方向进入后续链路。 */
    private int minSkillsOrResponsibilities = 1;
    private int maxSkills = 20;
    private int maxResponsibilities = 12;
    /** 每个岗位事实字段的最大长度，防止模型用超长自由文本绕过结构化约束。 */
    private int maxFieldValueLength = 100;
    /** 单个岗位事实列表的总字符上限，避免多个合法短项拼接成超长上下文。 */
    private int maxListCharacters = 600;
    private int maxInputLength = 12_000;
    private int maxLineLength = 2_000;
}
