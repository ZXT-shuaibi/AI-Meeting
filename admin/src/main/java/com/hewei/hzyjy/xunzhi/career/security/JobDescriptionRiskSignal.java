package com.hewei.hzyjy.xunzhi.career.security;

/** 仅描述风险类别，避免安全审计保存攻击载荷本身。 */
public enum JobDescriptionRiskSignal {
    INSTRUCTION_OVERRIDE,
    SYSTEM_PROMPT_REQUEST,
    ROLE_IMPERSONATION,
    SCORE_MANIPULATION,
    TOOL_CALL_REQUEST,
    DATA_EXFILTRATION,
    COMMAND_EXECUTION,
    ENCODED_PAYLOAD,
    EXTERNAL_URL,
    PSEUDO_XML,
    EXCESSIVE_LINE_LENGTH,
    REPETITIVE_TEXT
}
