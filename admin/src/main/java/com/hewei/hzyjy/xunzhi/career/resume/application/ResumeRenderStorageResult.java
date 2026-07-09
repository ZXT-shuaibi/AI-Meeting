package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.hewei.hzyjy.xunzhi.career.resume.render.ResumeRenderArtifact;

public record ResumeRenderStorageResult(
        ResumeRenderArtifact artifact,
        ResumeObjectStorageResult storage
) {
}
