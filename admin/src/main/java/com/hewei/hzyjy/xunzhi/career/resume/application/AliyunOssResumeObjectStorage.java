package com.hewei.hzyjy.xunzhi.career.resume.application;

import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;

public class AliyunOssResumeObjectStorage implements ResumeObjectStorage {

    private static final String PROVIDER = "aliyun-oss";

    private final String endpoint;
    private final String region;
    private final String bucketName;
    private final String publicBaseUrl;
    private final OssClientBridge client;

    public AliyunOssResumeObjectStorage(String endpoint, String region, String bucketName, String publicBaseUrl) {
        this(endpoint, region, bucketName, publicBaseUrl, new LazyReflectiveAliyunOssClientBridge(endpoint, region));
    }

    AliyunOssResumeObjectStorage(String endpoint, String region, String bucketName, String publicBaseUrl, OssClientBridge client) {
        this.endpoint = required(endpoint, "endpoint");
        this.region = required(region, "region");
        this.bucketName = required(bucketName, "bucketName");
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.strip();
        this.client = client;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public ResumeObjectStorageResult put(String key, byte[] content, String contentType, String originalFilename) {
        String normalizedKey = normalizeKey(key);
        try {
            ensureBucket();
            client.putObject(bucketName, normalizedKey, new ByteArrayInputStream(content == null ? new byte[0] : content), safeContentType(contentType));
            return new ResumeObjectStorageResult(PROVIDER, normalizedKey, publicPath(normalizedKey));
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Aliyun OSS resume object storage write failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public InputStream get(String key) {
        String normalizedKey = normalizeKey(key);
        try {
            return client.getObject(bucketName, normalizedKey);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Aliyun OSS resume object storage read failed: " + ex.getMessage(), ex);
        }
    }

    private void ensureBucket() throws Exception {
        if (!client.doesBucketExist(bucketName)) {
            client.createBucket(bucketName);
        }
    }

    private String publicPath(String key) {
        if (StringUtils.hasText(publicBaseUrl)) {
            return publicBaseUrl.replaceAll("/+$", "") + "/" + key;
        }
        return "https://" + bucketName + "." + endpoint.replaceAll("^https?://", "").replaceAll("/+$", "") + "/" + key;
    }

    private String normalizeKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("Resume object storage key is blank");
        }
        String normalized = key.replace('\\', '/').replaceAll("^/+", "");
        if (normalized.contains("..") || normalized.startsWith("/") || normalized.isBlank()) {
            throw new IllegalArgumentException("Invalid resume object storage key: " + key);
        }
        return normalized;
    }

    private String safeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
    }

    private static String required(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Aliyun OSS " + name + " must not be blank");
        }
        return value.strip();
    }

    public interface OssClientBridge {
        boolean doesBucketExist(String bucketName) throws Exception;

        void createBucket(String bucketName) throws Exception;

        void putObject(String bucketName, String key, InputStream inputStream, String contentType) throws Exception;

        InputStream getObject(String bucketName, String key) throws Exception;
    }

    private static final class LazyReflectiveAliyunOssClientBridge implements OssClientBridge {
        private final String endpoint;
        private final String region;
        private volatile OssClientBridge delegate;

        private LazyReflectiveAliyunOssClientBridge(String endpoint, String region) {
            this.endpoint = endpoint;
            this.region = region;
        }

        @Override
        public boolean doesBucketExist(String bucketName) throws Exception {
            return delegate().doesBucketExist(bucketName);
        }

        @Override
        public void createBucket(String bucketName) throws Exception {
            delegate().createBucket(bucketName);
        }

        @Override
        public void putObject(String bucketName, String key, InputStream inputStream, String contentType) throws Exception {
            delegate().putObject(bucketName, key, inputStream, contentType);
        }

        @Override
        public InputStream getObject(String bucketName, String key) throws Exception {
            return delegate().getObject(bucketName, key);
        }

        private OssClientBridge delegate() {
            OssClientBridge current = delegate;
            if (current == null) {
                synchronized (this) {
                    current = delegate;
                    if (current == null) {
                        current = ReflectiveAliyunOssClientBridge.create(endpoint, region);
                        delegate = current;
                    }
                }
            }
            return current;
        }
    }

    private static final class ReflectiveAliyunOssClientBridge implements OssClientBridge {
        private final Object ossClient;

        private ReflectiveAliyunOssClientBridge(Object ossClient) {
            this.ossClient = ossClient;
        }

        static ReflectiveAliyunOssClientBridge create(String endpoint, String region) {
            required(endpoint, "endpoint");
            required(region, "region");
            try {
                Object credentialsProvider = Class.forName("com.aliyun.oss.common.auth.CredentialsProviderFactory")
                        .getMethod("newEnvironmentVariableCredentialsProvider")
                        .invoke(null);
                Class<?> credentialsProviderType = Class.forName("com.aliyun.oss.common.auth.CredentialsProvider");
                Object configuration = Class.forName("com.aliyun.oss.ClientBuilderConfiguration")
                        .getConstructor()
                        .newInstance();
                Object signVersionV4 = Class.forName("com.aliyun.oss.common.comm.SignVersion")
                        .getField("V4")
                        .get(null);
                configuration.getClass()
                        .getMethod("setSignatureVersion", signVersionV4.getClass())
                        .invoke(configuration, signVersionV4);

                Object builder = Class.forName("com.aliyun.oss.OSSClientBuilder")
                        .getMethod("create")
                        .invoke(null);
                builder = builder.getClass().getMethod("endpoint", String.class).invoke(builder, endpoint);
                builder = builder.getClass().getMethod("credentialsProvider", credentialsProviderType).invoke(builder, credentialsProvider);
                builder = builder.getClass().getMethod("region", String.class).invoke(builder, region);
                builder = builder.getClass().getMethod("clientConfiguration", configuration.getClass()).invoke(builder, configuration);
                Object client = builder.getClass().getMethod("build").invoke(builder);
                return new ReflectiveAliyunOssClientBridge(client);
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException("Aliyun OSS SDK is not on the runtime classpath. Add com.aliyun.oss:aliyun-sdk-oss to enable provider=aliyun-oss.", ex);
            } catch (Exception ex) {
                throw new IllegalStateException("Aliyun OSS client initialization failed: " + ex.getMessage(), ex);
            }
        }

        @Override
        public boolean doesBucketExist(String bucketName) throws Exception {
            return (boolean) ossClient.getClass().getMethod("doesBucketExist", String.class).invoke(ossClient, bucketName);
        }

        @Override
        public void createBucket(String bucketName) throws Exception {
            ossClient.getClass().getMethod("createBucket", String.class).invoke(ossClient, bucketName);
        }

        @Override
        public void putObject(String bucketName, String key, InputStream inputStream, String contentType) throws Exception {
            Object metadata = buildObjectMetadata(contentType);
            Method method = ossClient.getClass().getMethod("putObject", String.class, String.class, InputStream.class, metadata.getClass());
            method.invoke(ossClient, bucketName, key, inputStream, metadata);
        }

        @Override
        public InputStream getObject(String bucketName, String key) throws Exception {
            Object object = ossClient.getClass().getMethod("getObject", String.class, String.class).invoke(ossClient, bucketName, key);
            return (InputStream) object.getClass().getMethod("getObjectContent").invoke(object);
        }

        private Object buildObjectMetadata(String contentType) throws Exception {
            Object metadata = Class.forName("com.aliyun.oss.model.ObjectMetadata")
                    .getConstructor()
                    .newInstance();
            metadata.getClass().getMethod("setContentType", String.class).invoke(metadata, contentType);
            return metadata;
        }
    }
}
