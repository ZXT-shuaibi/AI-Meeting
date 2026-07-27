package com.hewei.hzyjy.xunzhi.career.resume.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XunfeiPdfOcrHttpTransportTest {

    @Test
    void usesConfiguredOcrTimeoutInsteadOfOkHttpDefault() {
        ResumeOcrProperties properties = new ResumeOcrProperties();
        properties.setTimeoutSeconds(90);

        XunfeiPdfOcrHttpTransport transport = new XunfeiPdfOcrHttpTransport(properties);

        assertEquals(90_000, transport.readTimeoutMillis());
        assertEquals(90_000, transport.callTimeoutMillis());
    }
}
