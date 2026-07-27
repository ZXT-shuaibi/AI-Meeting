package com.hewei.hzyjy.xunzhi.career.raglab.model;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * RAG 实验室中简历标签的受限分类。
 *
 * <p>枚举只约束标签属于“岗位”还是“方向”；标签内容仍由用户自由输入，
 * 例如岗位可以是“后端开发”“产品经理”，方向可以是“Java”“RAG”“增长”。</p>
 */
@Getter
@RequiredArgsConstructor
public enum RagResumeTagType {

    /** 求职岗位或职能分类。 */
    ROLE("ROLE"),

    /** 技术、业务或职业方向分类。 */
    DIRECTION("DIRECTION"),

    /** 简历项目名称、项目角色或项目领域。 */
    PROJECT("PROJECT"),

    /** 技术、工具、方法论或行业技能。 */
    SKILL("SKILL"),

    /** 工作公司、工作角色、行业或工作经历摘要。 */
    EXPERIENCE("EXPERIENCE");

    @EnumValue
    private final String value;
}
