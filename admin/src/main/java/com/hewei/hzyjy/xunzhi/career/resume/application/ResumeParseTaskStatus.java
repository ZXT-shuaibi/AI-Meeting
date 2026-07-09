package com.hewei.hzyjy.xunzhi.career.resume.application;

public enum ResumeParseTaskStatus {
    PROCESSING("处理中"),
    ANALYZING("解析中"),
    SAVING("存储中"),
    COMPLETED("完成"),
    FAILED("失败"),
    CANCELED("已取消");

    private final String message;

    ResumeParseTaskStatus(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED;
    }
}
