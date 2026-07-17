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

    /**
     * Maximum time to wait for a complete response from the OpenAI-compatible
     * provider. Agentic resume optimization sends larger structured prompts and
     * may legitimately need longer than a normal chat turn.
     */
    private int timeoutSeconds = 90;

    /**
     * Keep false unless LangChain4j direct Agentic invocation is upgraded or wrapped
     * to initialize its managed ThreadLocal before native listener/tool callbacks.
     */
    private boolean agenticNativeListenersEnabled = false;

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
