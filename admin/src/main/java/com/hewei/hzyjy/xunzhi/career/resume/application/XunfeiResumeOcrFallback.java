package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hewei.hzyjy.xunzhi.common.config.xunfei.XunfeiLatProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class XunfeiResumeOcrFallback implements ResumeOcrFallback {

    private final ResumeOcrProperties properties;
    private final XunfeiLatProperties xunfeiProperties;
    private final XunfeiPdfOcrTransport transport;

    @Override
    public String extract(byte[] pdfBytes, int pageCount) throws Exception {
        if (!properties.isEnabled() || !"xunfei-pdf".equalsIgnoreCase(properties.getProvider())) {
            return "";
        }
        if (pageCount > properties.getMaxPages()) {
            throw new IllegalArgumentException("Scanned PDF resume exceeds OCR page limit: " + properties.getMaxPages());
        }
        String appId = resolvedAppId();
        String apiSecret = resolvedApiSecret();
        if (isBlank(appId) || isBlank(apiSecret)) {
            throw new IllegalStateException("Xunfei PDF OCR AppID or APISecret is not configured");
        }

        String taskNo = start(appId, apiSecret, pdfBytes);
        long deadlineMillis = System.currentTimeMillis() + Math.max(1, properties.getTimeoutSeconds()) * 1000L;
        // The PDF OCR API is asynchronous: submit once, then poll the task until its export is ready.
        while (true) {
            JSONObject statusPayload = responsePayload(transport.status(appId, timestamp(), signature(appId, apiSecret), taskNo));
            JSONObject data = statusPayload.getJSONObject("data");
            String status = data == null ? null : data.getString("status");
            if ("FINISH".equals(status)) {
                String downUrl = data.getString("downUrl");
                if (isBlank(downUrl)) {
                    throw new IllegalStateException("Xunfei PDF OCR finished without a result download URL");
                }
                String text = stripInlineImages(new String(transport.download(downUrl), StandardCharsets.UTF_8)).trim();
                if (text.isBlank()) {
                    throw new IllegalStateException("Xunfei PDF OCR completed without extracted text");
                }
                log.info("Xunfei PDF OCR completed for scanned resume, pageCount={}, textLength={}", pageCount, text.length());
                return text;
            }
            if ("FAILED".equals(status) || "ANY_FAILED".equals(status) || "STOP".equals(status)) {
                throw new IllegalStateException("Xunfei PDF OCR task " + status + ": " + safeText(data.getString("tip")));
            }
            if (System.currentTimeMillis() >= deadlineMillis) {
                throw new IllegalStateException("Xunfei PDF OCR timed out waiting for task completion");
            }
            Thread.sleep(Math.max(5, properties.getPollIntervalSeconds()) * 1000L);
        }
    }

    String resolvedAppId() {
        return firstNonBlank(properties.getAppId(), xunfeiProperties.getAppId());
    }

    String resolvedApiSecret() {
        return firstNonBlank(properties.getApiSecret(), xunfeiProperties.getApiSecret());
    }

    private String start(String appId, String apiSecret, byte[] pdfBytes) throws Exception {
        JSONObject payload = responsePayload(transport.start(
                appId, timestamp(), signature(appId, apiSecret), pdfBytes, "markdown"));
        JSONObject data = payload.getJSONObject("data");
        String taskNo = data == null ? null : data.getString("taskNo");
        if (isBlank(taskNo)) {
            throw new IllegalStateException("Xunfei PDF OCR did not return a task number");
        }
        return taskNo;
    }

    private JSONObject responsePayload(String response) {
        JSONObject payload = JSON.parseObject(response);
        if (payload == null || payload.getIntValue("code") != 0) {
            throw new IllegalStateException(failureMessage(payload));
        }
        return payload;
    }

    private String failureMessage(JSONObject payload) {
        if (payload == null) {
            return "Xunfei PDF OCR returned an invalid response";
        }
        String description = safeText(payload.getString("desc"));
        return "Xunfei PDF OCR request failed with code=" + payload.getIntValue("code")
                + (description.isBlank() ? "" : ": " + description);
    }

    private String timestamp() {
        return Long.toString(System.currentTimeMillis() / 1000L);
    }

    static String signature(String appId, String apiSecret) throws Exception {
        return XunfeiPdfOcrSignature.create(appId, apiSecret, System.currentTimeMillis() / 1000L);
    }

    private String firstNonBlank(String preferred, String fallback) {
        return !isBlank(preferred) ? preferred : fallback;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safeText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.substring(0, Math.min(normalized.length(), 160));
    }

    private String stripInlineImages(String markdown) {
        return markdown == null ? "" : markdown.replaceAll("!\\[[^]]*]\\(data:image/[^)]*\\)", "");
    }
}
