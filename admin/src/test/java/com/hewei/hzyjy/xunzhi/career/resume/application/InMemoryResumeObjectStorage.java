package com.hewei.hzyjy.xunzhi.career.resume.application;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class InMemoryResumeObjectStorage implements ResumeObjectStorage {

    private final ConcurrentMap<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public ResumeObjectStorageResult put(String key, byte[] content, String contentType, String originalFilename) {
        objects.put(key, content == null ? new byte[0] : content.clone());
        return new ResumeObjectStorageResult("memory-object-storage", key, "memory://" + key);
    }

    @Override
    public InputStream get(String key) {
        byte[] bytes = objects.get(key);
        if (bytes == null) {
            throw new IllegalArgumentException("Object not found: " + key);
        }
        return new ByteArrayInputStream(bytes);
    }

    boolean contains(String key) {
        return objects.containsKey(key);
    }
}
