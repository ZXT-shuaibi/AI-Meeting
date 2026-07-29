package com.hewei.hzyjy.xunzhi.career.security;

/** JD 安全策略开关。生产环境默认 ENFORCE；AUDIT 用于灰度观察，OFF 仅用于本地兼容排障。 */
public enum JobDescriptionSafetyMode {
    OFF,
    AUDIT,
    ENFORCE
}
