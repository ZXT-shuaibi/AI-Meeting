package com.hewei.hzyjy.xunzhi.media.infrastructure.integration;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.hewei.hzyjy.xunzhi.common.config.xunfei.XunfeiLatProperties;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ServiceException;
import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import com.hewei.hzyjy.xunzhi.media.api.io.req.LongTextTtsReqDTO;
import com.hewei.hzyjy.xunzhi.media.api.io.resp.LongTextTtsTaskRespDTO;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于讯飞 HTTP 接口的长文本语音合成服务。
 *
 * <p>鉴权与请求字段遵循讯飞长文本语音合成官方协议：
 * https://www.xfyun.cn/doc/tts/long_text_tts/API.html</p>
 */
@Slf4j
@Service
public class XunfeiLongTextTtsService {

    private static final int MAX_TEXT_LENGTH = 100000;
    private static final DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                    .withZone(ZoneOffset.UTC);
    private static final String HOST = "api-dx.xf-yun.com";
    private static final String SCHEME = "https";
    private static final String DTS_CREATE_PATH = "/v1/private/dts_create";
    private static final String DTS_QUERY_PATH = "/v1/private/dts_query";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final XunfeiLatProperties xunfeiLatProperties;
    private final ObjectProvider<AiTracePublisher> tracePublisherProvider;

    public XunfeiLongTextTtsService(XunfeiLatProperties xunfeiLatProperties) {
        this(xunfeiLatProperties, null);
    }

    @Autowired
    public XunfeiLongTextTtsService(
            XunfeiLatProperties xunfeiLatProperties,
            ObjectProvider<AiTracePublisher> tracePublisherProvider) {
        this.xunfeiLatProperties = xunfeiLatProperties;
        this.tracePublisherProvider = tracePublisherProvider;
    }

    public LongTextTtsTaskRespDTO createTask(LongTextTtsReqDTO requestParam) {
        String traceId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        AiTracePublisher tracePublisher = tracePublisher();
        String input = requestParam == null ? null : "textLength=" + (requestParam.getText() == null ? 0 : requestParam.getText().length());
        if (tracePublisher != null) {
            tracePublisher.started(traceId, "LEGACY_XUNFEI_TTS_CREATE", "xunfei-tts", "xunfei", "long-text-tts", input);
        }
        try {
            validateCredentials();
            validateCreateRequest(requestParam);

            JSONObject body = buildCreateTaskBody(requestParam);
            JSONObject response = postWithSign(DTS_CREATE_PATH, body);
            LongTextTtsTaskRespDTO result = parseTaskResponse(response);
            if (!Integer.valueOf(0).equals(result.getCode())) {
                throw new ServiceException("Failed to create TTS task: " + result.getMessage());
            }
            if (StrUtil.isBlank(result.getTaskId())) {
                throw new ServiceException("TTS task was created but taskId is missing");
            }
            result.setSuccess(true);
            result.setCompleted(false);
            if (tracePublisher != null) {
                tracePublisher.completed(traceId, "LEGACY_XUNFEI_TTS_CREATE", "xunfei-tts", "xunfei",
                        "long-text-tts", start, "taskId=" + result.getTaskId(), Map.of("taskId", result.getTaskId()));
            }
            return result;
        } catch (RuntimeException ex) {
            if (tracePublisher != null) {
                tracePublisher.failed(traceId, "LEGACY_XUNFEI_TTS_CREATE", "xunfei-tts", "xunfei",
                        "long-text-tts", start, ex);
            }
            throw ex;
        }
    }

    public LongTextTtsTaskRespDTO queryTask(String taskId) {
        String traceId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        AiTracePublisher tracePublisher = tracePublisher();
        if (tracePublisher != null) {
            tracePublisher.started(traceId, "LEGACY_XUNFEI_TTS_QUERY", "xunfei-tts:" + StrUtil.blankToDefault(taskId, "unknown"),
                    "xunfei", "long-text-tts", "taskId=" + taskId);
        }
        try {
            validateCredentials();
            if (StrUtil.isBlank(taskId)) {
                throw new ClientException("taskId must not be blank");
            }

            JSONObject body = new JSONObject();
            JSONObject header = new JSONObject();
            header.put("app_id", normalize(xunfeiLatProperties.getAppId()));
            header.put("task_id", taskId.trim());
            body.put("header", header);

            JSONObject response = postWithSign(DTS_QUERY_PATH, body);
            LongTextTtsTaskRespDTO result = parseTaskResponse(response);
            if (!Integer.valueOf(0).equals(result.getCode())) {
                throw new ServiceException("Failed to query TTS task: " + result.getMessage());
            }
            enrichDownloadedArtifacts(result);
            if (tracePublisher != null) {
                tracePublisher.completed(traceId, "LEGACY_XUNFEI_TTS_QUERY", "xunfei-tts:" + taskId.trim(), "xunfei",
                        "long-text-tts", start, "status=" + result.getTaskStatus(), Map.of("taskId", taskId.trim()));
            }
            return result;
        } catch (RuntimeException ex) {
            if (tracePublisher != null) {
                tracePublisher.failed(traceId, "LEGACY_XUNFEI_TTS_QUERY", "xunfei-tts:" + StrUtil.blankToDefault(taskId, "unknown"),
                        "xunfei", "long-text-tts", start, ex);
            }
            throw ex;
        }
    }

    public LongTextTtsTaskRespDTO synthesizeAndWait(LongTextTtsReqDTO requestParam) {
        LongTextTtsTaskRespDTO created = createTask(requestParam);
        String taskId = created.getTaskId();
        if (StrUtil.isBlank(taskId)) {
            throw new ServiceException("taskId is missing after TTS task creation");
        }

        int timeoutSeconds = requestParam != null && requestParam.getTimeoutSeconds() != null
                ? Math.max(10, requestParam.getTimeoutSeconds())
                : 90;
        int pollIntervalMs = requestParam != null && requestParam.getPollIntervalMs() != null
                ? Math.max(500, requestParam.getPollIntervalMs())
                : 1500;

        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        LongTextTtsTaskRespDTO latest = created;
        while (System.currentTimeMillis() < deadline) {
            latest = queryTask(taskId);
            String status = latest.getTaskStatus();
            if ("5".equals(status)) {
                latest.setCompleted(true);
                latest.setSuccess(true);
                enrichDownloadedArtifacts(latest);
                return latest;
            }
            if ("2".equals(status) || "4".equals(status)) {
                latest.setCompleted(true);
                latest.setSuccess(false);
                throw new ServiceException("TTS task failed, taskId=" + taskId + ", status=" + status
                        + ", message=" + latest.getMessage());
            }
            sleepQuietly(pollIntervalMs);
        }
        throw new ServiceException("Timed out waiting for TTS task, taskId=" + taskId
                + ", timeoutSeconds=" + timeoutSeconds);
    }

    private void validateCreateRequest(LongTextTtsReqDTO requestParam) {
        if (requestParam == null || StrUtil.isBlank(requestParam.getText())) {
            throw new ClientException("text must not be blank");
        }
        if (requestParam.getText().length() > MAX_TEXT_LENGTH) {
            throw new ClientException("text length must not exceed " + MAX_TEXT_LENGTH + " characters");
        }
        validateRange("speed", requestParam.getSpeed(), 0, 100);
        validateRange("volume", requestParam.getVolume(), 0, 100);
        validateRange("pitch", requestParam.getPitch(), 0, 100);
        validateBinary("rhy", requestParam.getRhy());
    }

    private void validateCredentials() {
        String appId = normalize(xunfeiLatProperties.getAppId());
        String apiKey = normalize(xunfeiLatProperties.getApiKey());
        String apiSecret = normalize(xunfeiLatProperties.getApiSecret());
        if (StrUtil.hasBlank(appId, apiKey, apiSecret)) {
            throw new ServiceException("Xunfei credentials are missing: appId/apiKey/apiSecret");
        }
    }

    private JSONObject buildCreateTaskBody(LongTextTtsReqDTO requestParam) {
        JSONObject body = new JSONObject();

        JSONObject header = new JSONObject();
        header.put("app_id", normalize(xunfeiLatProperties.getAppId()));
        body.put("header", header);

        JSONObject parameter = new JSONObject();
        JSONObject dts = new JSONObject();
        dts.put("vcn", defaultIfBlank(requestParam.getVcn(), "x4_mingge"));
        dts.put("language", defaultIfBlank(requestParam.getLanguage(), "zh"));
        dts.put("speed", defaultIfNull(requestParam.getSpeed(), 50));
        dts.put("volume", defaultIfNull(requestParam.getVolume(), 50));
        dts.put("pitch", defaultIfNull(requestParam.getPitch(), 50));
        dts.put("rhy", defaultIfNull(requestParam.getRhy(), 0));
        dts.put("audio", buildAudioConfig(requestParam));
        dts.put("pybuf", buildPybufConfig());
        parameter.put("dts", dts);
        body.put("parameter", parameter);

        JSONObject payload = new JSONObject();
        JSONObject textNode = new JSONObject();
        textNode.put("encoding", "utf8");
        textNode.put("compress", "raw");
        textNode.put("format", "plain");
        textNode.put("text", Base64.getEncoder().encodeToString(requestParam.getText().getBytes(StandardCharsets.UTF_8)));
        payload.put("text", textNode);
        body.put("payload", payload);

        return body;
    }

    private JSONObject buildAudioConfig(LongTextTtsReqDTO requestParam) {
        JSONObject audio = new JSONObject();
        audio.put("encoding", defaultIfBlank(requestParam.getAudioEncoding(), "lame"));
        audio.put("sample_rate", defaultIfNull(requestParam.getSampleRate(), 16000));
        return audio;
    }

    private JSONObject buildPybufConfig() {
        JSONObject pybuf = new JSONObject();
        pybuf.put("encoding", "utf8");
        pybuf.put("compress", "raw");
        pybuf.put("format", "plain");
        return pybuf;
    }

    private JSONObject postWithSign(String path, JSONObject body) {
        SignedRequestInfo signedRequestInfo = buildSignedRequestInfo(path);
        String requestBody = body.toJSONString();
        // 签名 URL 与 authorization 含有短期凭证，日志仅保留请求路径和时间，避免凭证泄露。
        log.info("讯飞长文本语音请求已完成签名，路径={}，请求时间={}",
                path,
                signedRequestInfo.getDate());

        Request request = new Request.Builder()
                .url(signedRequestInfo.getUrl())
                .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE))
                .addHeader("Host", HOST)
                .addHeader("Date", signedRequestInfo.getDate())
                .addHeader("x-date", signedRequestInfo.getDate())
                .addHeader("Authorization", signedRequestInfo.getAuthorization())
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("讯飞长文本语音请求失败，路径={}，状态码={}，响应时间={}，响应内容={}",
                        path, response.code(), response.header("Date"), responseBody);
                throw new ServiceException("讯飞长文本语音请求失败，HTTP 状态码：" + response.code());
            }
            if (StrUtil.isBlank(responseBody)) {
                throw new ServiceException("Xunfei TTS response is empty");
            }
            return JSONObject.parseObject(responseBody);
        } catch (Exception ex) {
            throw new ServiceException("Failed to call Xunfei TTS: " + ex.getMessage());
        }
    }

    private SignedRequestInfo buildSignedRequestInfo(String path) {
        try {
            String host = HOST;
            String date = HTTP_DATE_FORMATTER.format(Instant.now());
            String requestLine = "POST " + path + " HTTP/1.1";
            String signatureOrigin = "host: " + host + "\n" + "date: " + date + "\n" + requestLine;

            String signature = hmacSha256Base64(signatureOrigin, normalize(xunfeiLatProperties.getApiSecret()));
            String authorizationOrigin = String.format(
                    "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
                    normalize(xunfeiLatProperties.getApiKey()), signature);
            String authorization = Base64.getEncoder()
                    .encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));

            HttpUrl url = new HttpUrl.Builder()
                    .scheme(SCHEME)
                    .host(host)
                    .encodedPath(path)
                    .addQueryParameter("host", host)
                    .addQueryParameter("date", date)
                    .addQueryParameter("authorization", authorization)
                    .build();
            return new SignedRequestInfo(url.toString(), date, authorization);
        } catch (Exception ex) {
            throw new ServiceException("Failed to build Xunfei TTS auth URL: " + ex.getMessage());
        }
    }

    private String hmacSha256Base64(String content, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signBytes = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signBytes);
    }

    private LongTextTtsTaskRespDTO parseTaskResponse(JSONObject response) {
        LongTextTtsTaskRespDTO result = new LongTextTtsTaskRespDTO();
        if (response == null) {
            result.setCode(-1);
            result.setMessage("empty response");
            result.setCompleted(true);
            result.setSuccess(false);
            return result;
        }

        JSONObject header = response.getJSONObject("header");
        result.setCode(header != null ? header.getInteger("code") : -1);
        result.setMessage(header != null ? header.getString("message") : "unknown");
        result.setSid(header != null ? header.getString("sid") : null);
        result.setTaskId(header != null ? header.getString("task_id") : null);
        result.setTaskStatus(header != null && header.get("task_status") != null
                ? String.valueOf(header.get("task_status"))
                : null);

        JSONObject payload = response.getJSONObject("payload");
        JSONObject audio = payload != null ? payload.getJSONObject("audio") : null;
        if (audio != null) {
            result.setAudioUrl(tryDecodeBase64Url(audio.getString("audio")));
        }
        JSONObject pybuf = payload != null ? payload.getJSONObject("pybuf") : null;
        if (pybuf != null) {
            result.setPybufUrl(tryDecodeBase64Url(pybuf.getString("text")));
        }

        result.setCompleted("5".equals(result.getTaskStatus()) || "2".equals(result.getTaskStatus())
                || "4".equals(result.getTaskStatus()));
        result.setSuccess(Integer.valueOf(0).equals(result.getCode()) && "5".equals(result.getTaskStatus()));
        return result;
    }

    private void enrichDownloadedArtifacts(LongTextTtsTaskRespDTO result) {
        if (result == null || !Boolean.TRUE.equals(result.getSuccess()) || !Boolean.TRUE.equals(result.getCompleted())) {
            return;
        }
        if (StrUtil.isNotBlank(result.getAudioUrl()) && StrUtil.isBlank(result.getAudioBase64())) {
            result.setAudioBase64(downloadBinaryAsBase64(result.getAudioUrl()));
        }
        if (StrUtil.isNotBlank(result.getPybufUrl()) && StrUtil.isBlank(result.getPybufContent())) {
            result.setPybufContent(downloadTextContent(result.getPybufUrl()));
        }
    }

    private String downloadBinaryAsBase64(String url) {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ServiceException("Failed to download TTS audio, HTTP status: " + response.code());
            }
            if (response.body() == null) {
                throw new ServiceException("Failed to download TTS audio: response body is empty");
            }
            byte[] bytes = response.body().bytes();
            if (bytes.length == 0) {
                throw new ServiceException("Failed to download TTS audio: content is empty");
            }
            return Base64.getEncoder().encodeToString(bytes);
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("Failed to download TTS audio: " + ex.getMessage());
        }
    }

    private String downloadTextContent(String url) {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ServiceException("Failed to download TTS pybuf, HTTP status: " + response.code());
            }
            if (response.body() == null) {
                throw new ServiceException("Failed to download TTS pybuf: response body is empty");
            }
            String content = response.body().string();
            if (StrUtil.isBlank(content)) {
                throw new ServiceException("Failed to download TTS pybuf: content is empty");
            }
            return content;
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("Failed to download TTS pybuf: " + ex.getMessage());
        }
    }

    private String tryDecodeBase64Url(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return value;
        }
    }

    private void sleepQuietly(long sleepMillis) {
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Polling for TTS task was interrupted");
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StrUtil.isBlank(value) ? defaultValue : value.trim();
    }

    private Integer defaultIfNull(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static final class SignedRequestInfo {
        private final String url;
        private final String date;
        private final String authorization;

        private SignedRequestInfo(String url, String date, String authorization) {
            this.url = url;
            this.date = date;
            this.authorization = authorization;
        }

        private String getUrl() {
            return url;
        }

        private String getDate() {
            return date;
        }

        private String getAuthorization() {
            return authorization;
        }

        private String getAuthorizationPrefix() {
            if (authorization == null) {
                return null;
            }
            return authorization.length() <= 24 ? authorization : authorization.substring(0, 24);
        }
    }

    private void validateRange(String fieldName, Integer value, int minInclusive, int maxInclusive) {
        if (value == null) {
            return;
        }
        if (value < minInclusive || value > maxInclusive) {
            throw new ClientException(fieldName + " must be between " + minInclusive + " and " + maxInclusive);
        }
    }

    private void validateBinary(String fieldName, Integer value) {
        if (value == null) {
            return;
        }
        if (value != 0 && value != 1) {
            throw new ClientException(fieldName + " must be 0 or 1");
        }
    }

    private AiTracePublisher tracePublisher() {
        return tracePublisherProvider == null ? null : tracePublisherProvider.getIfAvailable();
    }
}
