package com.hewei.hzyjy.xunzhi.career.agent.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CareerJsonResponseCleanerTest {

    @Test
    void returnsToolCallPayloadWithoutCleaning() {
        String raw = "<tool_calls><invoke name=\"activate_skill\"></invoke></tool_calls>";

        assertEquals(raw, CareerJsonResponseCleaner.cleanJsonResponse(raw));
    }

    @Test
    void extractsJsonFromTail() {
        String raw = """
                分析如下
                说明文字
                {"score":0.82,"feedback":"ok"}
                """;

        assertEquals("{\"score\":0.82,\"feedback\":\"ok\"}", CareerJsonResponseCleaner.cleanJsonResponse(raw));
    }

    @Test
    void extractsJsonByStrippingPrefix() {
        String raw = "prefix prefix {\"score\":0.75,\"feedback\":\"usable\"}";

        assertTrue(CareerJsonResponseCleaner.cleanJsonResponse(raw).contains("\"score\":0.75"));
    }

    @Test
    void extractsJsonFromLineStartingWithBrace() {
        String raw = """
                result:
                  {"score":0.91,"feedback":"strong fit"}
                trailing note
                """;

        assertEquals("{\"score\":0.91,\"feedback\":\"strong fit\"}", CareerJsonResponseCleaner.cleanJsonResponse(raw));
    }
}
