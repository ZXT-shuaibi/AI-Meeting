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
    DIRECTION("DIRECTION");

    @EnumValue
    private final String value;
}
