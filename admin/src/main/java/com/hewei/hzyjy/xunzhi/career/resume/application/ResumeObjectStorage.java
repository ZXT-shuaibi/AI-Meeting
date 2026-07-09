package com.hewei.hzyjy.xunzhi.career.resume.application;

import java.io.InputStream;

public interface ResumeObjectStorage {

    boolean enabled();

    ResumeObjectStorageResult put(String key, byte[] content, String contentType, String originalFilename);

    InputStream get(String key);

    static ResumeObjectStorage disabled() {
        return DisabledResumeObjectStorage.INSTANCE;
    }

    final class DisabledResumeObjectStorage implements ResumeObjectStorage {
        private static final DisabledResumeObjectStorage INSTANCE = new DisabledResumeObjectStorage();

        private DisabledResumeObjectStorage() {
        }

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public ResumeObjectStorageResult put(String key, byte[] content, String contentType, String originalFilename) {
            throw new UnsupportedOperationException("Resume object storage is disabled");
        }

        @Override
        public InputStream get(String key) {
            throw new UnsupportedOperationException("Resume object storage is disabled");
        }
    }
}
