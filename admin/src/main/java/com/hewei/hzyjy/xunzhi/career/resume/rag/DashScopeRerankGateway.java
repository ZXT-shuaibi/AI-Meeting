package com.hewei.hzyjy.xunzhi.career.resume.rag;

import com.hewei.hzyjy.xunzhi.career.config.CareerRagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeRerankGateway implements RerankGateway {

    private final CareerRagProperties properties;

    @Override
    public List<String> rerank(String query, List<String> candidates, int limit) {
        if (!properties.getRerank().isEnabled() || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        try {
            Class<?> textReRankClass = Class.forName("com.alibaba.dashscope.rerank.TextReRank");
            Class<?> paramClass = Class.forName("com.alibaba.dashscope.rerank.TextReRankParam");
            Object builder = paramClass.getMethod("builder").invoke(null);
            invokeBuilder(builder, "apiKey", apiKey());
            invokeBuilder(builder, "model", properties.getRerank().getModel());
            invokeBuilder(builder, "query", query);
            invokeBuilder(builder, "documents", candidates);
            invokeBuilder(builder, "topN", limit);
            invokeBuilder(builder, "returnDocuments", true);
            Object param = builder.getClass().getMethod("build").invoke(builder);
            Object reranker = textReRankClass.getConstructor().newInstance();
            Object result = reranker.getClass().getMethod("call", paramClass).invoke(reranker, param);
            return extractDocuments(result, limit);
        } catch (Exception ex) {
            log.warn("DashScope rerank unavailable, fallback ranking will be used", ex);
            return List.of();
        }
    }

    private String apiKey() {
        String configured = properties.getRerank().getApiKey();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return System.getenv("DASHSCOPE_API_KEY");
    }

    private void invokeBuilder(Object builder, String methodName, Object value) throws Exception {
        for (Method method : builder.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
                method.invoke(builder, value);
                return;
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    private List<String> extractDocuments(Object rerankResult, int limit) throws Exception {
        Object output = rerankResult.getClass().getMethod("getOutput").invoke(rerankResult);
        Object results = output.getClass().getMethod("getResults").invoke(output);
        if (!(results instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(this::extractText)
                .filter(text -> text != null && !text.isBlank())
                .limit(limit)
                .toList();
    }

    private String extractText(Object result) {
        try {
            Object document = result.getClass().getMethod("getDocument").invoke(result);
            Object text = document.getClass().getMethod("getText").invoke(document);
            return text == null ? null : String.valueOf(text);
        } catch (Exception ex) {
            return null;
        }
    }
}
