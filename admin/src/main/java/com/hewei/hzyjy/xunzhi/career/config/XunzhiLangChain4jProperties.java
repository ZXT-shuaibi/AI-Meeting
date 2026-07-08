package com.hewei.hzyjy.xunzhi.career.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "xunzhi-agent.langchain4j")
public class XunzhiLangChain4jProperties {

    private boolean enabled = true;

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private String apiKey = "";

    private String chatModel = "qwen-plus";

    private String embeddingModel = "text-embedding-v3";

    private Qdrant qdrant = new Qdrant();

    @Data
    public static class Qdrant {
        private boolean enabled = false;
        private String collectionName = "xunzhi_resume";
        private String host = "localhost";
        private int port = 6333;
        private boolean useTls = false;
        private String apiKey = "";
        private String payloadTextKey = "content";
        private int vectorSize = 1024;
        private int timeoutMillis = 3000;
    }
}
