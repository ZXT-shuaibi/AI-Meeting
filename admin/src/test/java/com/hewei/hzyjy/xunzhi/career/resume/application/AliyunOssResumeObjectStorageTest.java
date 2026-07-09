package com.hewei.hzyjy.xunzhi.career.resume.application;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AliyunOssResumeObjectStorageTest {

    @Test
    void writesAndReadsObjectsThroughClientBridge() throws Exception {
        FakeAliyunOssClient client = new FakeAliyunOssClient();
        AliyunOssResumeObjectStorage storage = new AliyunOssResumeObjectStorage(
                "oss-cn-beijing.aliyuncs.com",
                "cn-beijing",
                "xunzhi-resume",
                "https://cdn.example.com/resume",
                client
        );

        ResumeObjectStorageResult result = storage.put(
                "career/resume/parse-task/task-1/resume.pdf",
                "PDF".getBytes(StandardCharsets.UTF_8),
                "application/pdf",
                "resume.pdf"
        );

        assertThat(result.provider()).isEqualTo("aliyun-oss");
        assertThat(result.key()).isEqualTo("career/resume/parse-task/task-1/resume.pdf");
        assertThat(result.path()).isEqualTo("https://cdn.example.com/resume/career/resume/parse-task/task-1/resume.pdf");
        assertThat(client.bucketCreated).isTrue();
        assertThat(client.contentTypes.get(result.key())).isEqualTo("application/pdf");
        assertThat(new String(storage.get(result.key()).readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("PDF");
    }

    @Test
    void rejectsBlankBucketAndUnsafeKeys() {
        FakeAliyunOssClient client = new FakeAliyunOssClient();

        assertThatThrownBy(() -> new AliyunOssResumeObjectStorage("endpoint", "region", "", "", client))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket");

        AliyunOssResumeObjectStorage storage = new AliyunOssResumeObjectStorage("endpoint", "region", "bucket", "", client);
        assertThatThrownBy(() -> storage.put("../escape.pdf", new byte[]{1}, "application/pdf", "escape.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid resume object storage key");
    }

    private static class FakeAliyunOssClient implements AliyunOssResumeObjectStorage.OssClientBridge {
        private final Map<String, byte[]> objects = new HashMap<>();
        private final Map<String, String> contentTypes = new HashMap<>();
        private boolean bucketExists;
        private boolean bucketCreated;

        @Override
        public boolean doesBucketExist(String bucketName) {
            return bucketExists;
        }

        @Override
        public void createBucket(String bucketName) {
            bucketCreated = true;
            bucketExists = true;
        }

        @Override
        public void putObject(String bucketName, String key, InputStream inputStream, String contentType) throws Exception {
            objects.put(key, inputStream.readAllBytes());
            contentTypes.put(key, contentType);
        }

        @Override
        public InputStream getObject(String bucketName, String key) {
            return new ByteArrayInputStream(objects.get(key));
        }
    }
}
