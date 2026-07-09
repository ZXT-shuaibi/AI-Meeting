package com.hewei.hzyjy.xunzhi.career.resume.render;

public record ResumeHtmlConfig(
        String cssResourcePath,
        boolean twoColumnLayout,
        boolean showAvatar,
        boolean showSocial
) {
    public static ResumeHtmlConfig defaults() {
        return new ResumeHtmlConfig("templates/career-resume/style/cv.css", false, false, true);
    }
}
