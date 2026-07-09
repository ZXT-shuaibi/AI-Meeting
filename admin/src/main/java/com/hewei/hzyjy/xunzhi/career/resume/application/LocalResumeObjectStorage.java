package com.hewei.hzyjy.xunzhi.career.resume.application;

import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalResumeObjectStorage implements ResumeObjectStorage {

    private final String provider;
    private final Path baseDir;
    private final String publicBaseUrl;

    public LocalResumeObjectStorage(String provider, String baseDir, String publicBaseUrl) {
        this.provider = StringUtils.hasText(provider) ? provider : "local";
        this.baseDir = Path.of(resolveUserHome(StringUtils.hasText(baseDir) ? baseDir : "${user.home}/.xunzhi-agent/career/object-storage")).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.strip();
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public ResumeObjectStorageResult put(String key, byte[] content, String contentType, String originalFilename) {
        try {
            Path target = resolveSafePath(key);
            Files.createDirectories(target.getParent());
            Files.write(target, content == null ? new byte[0] : content);
            return new ResumeObjectStorageResult(provider, normalizeKey(key), publicPath(normalizeKey(key), target));
        } catch (Exception ex) {
            throw new IllegalStateException("Resume object storage write failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return Files.newInputStream(resolveSafePath(key));
        } catch (Exception ex) {
            throw new IllegalStateException("Resume object storage read failed: " + ex.getMessage(), ex);
        }
    }

    private Path resolveSafePath(String key) {
        Path target = baseDir.resolve(normalizeKey(key)).normalize();
        if (!target.startsWith(baseDir)) {
            throw new IllegalArgumentException("Invalid resume object storage key: " + key);
        }
        return target;
    }

    private String normalizeKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("Resume object storage key is blank");
        }
        return key.replace('\\', '/').replaceAll("^/+", "");
    }

    private String publicPath(String key, Path target) {
        if (!StringUtils.hasText(publicBaseUrl)) {
            return target.toString();
        }
        return publicBaseUrl.replaceAll("/+$", "") + "/" + key;
    }

    private static String resolveUserHome(String value) {
        return value.replace("${user.home}", System.getProperty("user.home"));
    }
}
