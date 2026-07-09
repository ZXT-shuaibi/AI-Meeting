package com.hewei.hzyjy.xunzhi.career.ai;

import com.hewei.hzyjy.xunzhi.career.config.XunzhiLangChain4jProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LangChain4jAgenticSafetyPolicy {

    private final XunzhiLangChain4jProperties properties;

    @Autowired
    public LangChain4jAgenticSafetyPolicy(ObjectProvider<XunzhiLangChain4jProperties> propertiesProvider) {
        this(propertiesProvider == null ? null : propertiesProvider.getIfAvailable(XunzhiLangChain4jProperties::new));
    }

    public LangChain4jAgenticSafetyPolicy(XunzhiLangChain4jProperties properties) {
        this.properties = properties;
    }

    public void assertNativeAgenticListenersDisabled() {
        if (properties == null || !properties.isAgenticNativeListenersEnabled()) {
            return;
        }
        throw new IllegalStateException("""
                LangChain4j Agentic native listeners are disabled for direct Agent calls because \
                langchain4j-agentic can miss its managed ThreadLocal in listener/tool callbacks. \
                Use AI-Meeting AiTracePublisher at the adapter/runtime boundary instead.
                """);
    }
}
