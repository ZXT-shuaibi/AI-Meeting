package com.hewei.hzyjy.xunzhi.career.resume.render;

public record ResumeRenderBundle(
        ResumeRenderArtifact markdown,
        ResumeRenderArtifact html,
        ResumeRenderArtifact pdf,
        ResumeRenderArtifact docx
) {
}
