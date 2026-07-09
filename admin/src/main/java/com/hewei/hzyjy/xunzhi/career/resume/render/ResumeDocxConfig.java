package com.hewei.hzyjy.xunzhi.career.resume.render;

public record ResumeDocxConfig(String fontFamily) {
    public static ResumeDocxConfig defaults() {
        return new ResumeDocxConfig("Microsoft YaHei");
    }
}
