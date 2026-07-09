package com.hewei.hzyjy.xunzhi.career.agent.support;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentResponseParserTest {

    @Test
    void cleansMarkdownFenceAndExplanatoryTextBeforeParsingJsonObject() {
        String response = """
                下面是结构化结果：
                ```json
                {"score":0.86,"feedback":"建议突出RAG和Agent闭环"}
                ```
                已按要求输出。
                """;

        JSONObject json = AgentResponseParser.jsonObject(response).orElseThrow();

        assertEquals(0.86, json.getDoubleValue("score"));
        assertEquals("建议突出RAG和Agent闭环", json.getString("feedback"));
    }

    @Test
    void repairsUnescapedControlCharactersInsideJsonString() {
        String response = "{\"score\":0.72,\"feedback\":\"第一行\n第二行\t补充\"}";

        JSONObject json = AgentResponseParser.jsonObject(response).orElseThrow();

        assertEquals(0.72, json.getDoubleValue("score"));
        assertEquals("第一行\n第二行\t补充", json.getString("feedback"));
    }

    @Test
    void extractsTailJsonWhenPreviousTextContainsBraces() {
        String response = """
                评分说明：请参考 {score: 0-1} 这个格式。
                最终结果如下：
                {"decision":"PROBE","score":5,"feedback":"需要追问项目指标"}
                """;

        assertEquals("PROBE", AgentResponseParser.decision(response).orElseThrow());
        assertTrue(AgentResponseParser.feedback(response).orElseThrow().contains("项目指标"));
    }

    @Test
    void keepsValidJsonWhenTrailingExplanationContainsBraces() {
        String response = """
                ```json
                {"score":0.91,"feedback":"已经达到投递标准"}
                ```
                字段说明：{score, feedback} 均已校验。
                """;

        JSONObject json = AgentResponseParser.jsonObject(response).orElseThrow();

        assertEquals(0.91, json.getDoubleValue("score"));
        assertEquals("已经达到投递标准", json.getString("feedback"));
    }

    @Test
    void prefersFinalResultJsonOverEarlierExampleJson() {
        String response = """
                示例：{"score":0}
                最终结果：
                {"decision":"PROBE","score":5,"feedback":"需要追问项目指标"}
                """;

        JSONObject json = AgentResponseParser.jsonObject(response).orElseThrow();

        assertEquals("PROBE", json.getString("decision"));
        assertEquals("需要追问项目指标", json.getString("feedback"));
    }
}
