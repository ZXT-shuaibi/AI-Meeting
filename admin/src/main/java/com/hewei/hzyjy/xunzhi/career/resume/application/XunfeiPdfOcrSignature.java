package com.hewei.hzyjy.xunzhi.career.resume.application;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

final class XunfeiPdfOcrSignature {

    private XunfeiPdfOcrSignature() {
    }

    static String create(String appId, String apiSecret, long timestamp) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] md5 = digest.digest((appId + timestamp).getBytes(StandardCharsets.UTF_8));
        StringBuilder auth = new StringBuilder(md5.length * 2);
        for (byte value : md5) {
            auth.append(String.format("%02x", value));
        }
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(auth.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
