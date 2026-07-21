package com.hewei.hzyjy.xunzhi.career.resume.application;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class XunfeiPdfOcrHttpTransport implements XunfeiPdfOcrTransport {

    private static final String BASE_URL = "https://iocr.xfyun.cn/ocrzdq";
    private static final MediaType PDF_MEDIA_TYPE = MediaType.get("application/pdf");
    private final OkHttpClient client;

    public XunfeiPdfOcrHttpTransport(ResumeOcrProperties properties) {
        long timeoutSeconds = Math.max(1, properties == null ? 90 : properties.getTimeoutSeconds());
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    long readTimeoutMillis() {
        return client.readTimeoutMillis();
    }

    long callTimeoutMillis() {
        return client.callTimeoutMillis();
    }

    @Override
    public String start(String appId, String timestamp, String signature, byte[] pdfBytes, String exportFormat) throws Exception {
        RequestBody file = RequestBody.create(pdfBytes, PDF_MEDIA_TYPE);
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "resume.pdf", file)
                .addFormDataPart("fileName", "resume.pdf")
                .addFormDataPart("exportFormat", exportFormat)
                .build();
        Request request = request(BASE_URL + "/v1/pdfOcr/start", appId, timestamp, signature)
                .post(body)
                .build();
        return executeText(request);
    }

    @Override
    public String status(String appId, String timestamp, String signature, String taskNo) throws Exception {
        HttpUrl url = HttpUrl.get(BASE_URL + "/v1/pdfOcr/status").newBuilder()
                .addQueryParameter("taskNo", taskNo)
                .build();
        Request request = request(url.toString(), appId, timestamp, signature).get().build();
        return executeText(request);
    }

    @Override
    public byte[] download(String url) throws Exception {
        try (Response response = client.newCall(new Request.Builder().url(url).get().build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Failed to download Xunfei PDF OCR result, httpStatus=" + response.code());
            }
            return response.body().bytes();
        }
    }

    private Request.Builder request(String url, String appId, String timestamp, String signature) {
        return new Request.Builder()
                .url(url)
                .header("appId", appId)
                .header("timestamp", timestamp)
                .header("signature", signature);
    }

    private String executeText(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Xunfei PDF OCR request failed, httpStatus=" + response.code());
            }
            return response.body().string();
        }
    }
}
