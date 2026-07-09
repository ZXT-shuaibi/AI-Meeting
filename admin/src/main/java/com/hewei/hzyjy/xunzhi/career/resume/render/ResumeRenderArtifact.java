package com.hewei.hzyjy.xunzhi.career.resume.render;

public record ResumeRenderArtifact(
        String format,
        String filename,
        String contentType,
        String content,
        byte[] bytes
) {
    public ResumeRenderArtifact {
        content = content == null ? "" : content;
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    static ResumeRenderArtifact text(String format, String filename, String contentType, String content) {
        return new ResumeRenderArtifact(format, filename, contentType, content, content == null ? new byte[0] : content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static ResumeRenderArtifact binary(String format, String filename, String contentType, byte[] bytes) {
        return new ResumeRenderArtifact(format, filename, contentType, "", bytes);
    }
}
