package com.hewei.hzyjy.xunzhi.career.memory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class HybridCompactingChatMemory {

    private static final int DEFAULT_COMPACT_THRESHOLD = 30;
    private static final int DEFAULT_RECENT_MESSAGE_COUNT = 6;
    private static final String REDIS_KEY_PREFIX = "xunzhi-agent:career:memory:messages:";
    private static final Duration REDIS_TTL = Duration.ofDays(7);

    private final AiGateway aiGateway;
    private final ImportanceScorer importanceScorer;
    private final DecisionIndex decisionIndex;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Map<String, List<MemoryMessage>> store = new ConcurrentHashMap<>();

    public HybridCompactingChatMemory(AiGateway aiGateway, ImportanceScorer importanceScorer, DecisionIndex decisionIndex) {
        this(aiGateway, importanceScorer, decisionIndex, null);
    }

    @Autowired
    public HybridCompactingChatMemory(
            AiGateway aiGateway,
            ImportanceScorer importanceScorer,
            DecisionIndex decisionIndex,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.aiGateway = aiGateway;
        this.importanceScorer = importanceScorer;
        this.decisionIndex = decisionIndex;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public void add(String memoryId, MemoryMessage message) {
        List<MemoryMessage> messages = new ArrayList<>(load(memoryId));
        messages.add(message);
        MemoryImportance importance = importanceScorer.score(message, messages);
        if (importance == MemoryImportance.HIGH) {
            decisionIndex.record(memoryId, messages.size() - 1, extractDecisionSummary(message));
        }
        if (messages.size() > DEFAULT_COMPACT_THRESHOLD) {
            messages = compact(memoryId, messages, DEFAULT_RECENT_MESSAGE_COUNT);
        }
        List<MemoryMessage> snapshot = List.copyOf(messages);
        store.put(memoryId, snapshot);
        persist(memoryId, snapshot);
    }

    public CompactedMemoryView view(String memoryId, int recentDecisionLimit) {
        List<MemoryMessage> messages = List.copyOf(load(memoryId));
        return CompactedMemoryView.builder()
                .memoryId(memoryId)
                .messages(messages)
                .decisions(decisionIndex.getRecentDecisions(memoryId, recentDecisionLimit))
                .decisionContext(decisionIndex.formatDecisionContext(memoryId, recentDecisionLimit))
                .build();
    }

    public List<MemoryMessage> messages(String memoryId) {
        return List.copyOf(load(memoryId));
    }

    public void clear(String memoryId) {
        store.remove(memoryId);
        decisionIndex.clear(memoryId);
        StringRedisTemplate redisTemplate = redisTemplate();
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(redisKey(memoryId));
            } catch (Exception ignored) {
                // Redis is best-effort recovery only.
            }
        }
    }

    private List<MemoryMessage> compact(String memoryId, List<MemoryMessage> messages, int recentMessageCount) {
        int recentBoundary = Math.max(messages.size() - recentMessageCount, 0);
        List<MemoryMessage> pinned = new ArrayList<>();
        List<MemoryMessage> compressible = new ArrayList<>();
        List<MemoryMessage> recent = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            MemoryMessage message = messages.get(i);
            if (i >= recentBoundary) {
                recent.add(message);
            } else if (importanceScorer.score(message, messages) == MemoryImportance.HIGH) {
                pinned.add(message);
            } else {
                compressible.add(message);
            }
        }

        List<MemoryMessage> result = new ArrayList<>();
        if (!compressible.isEmpty()) {
            result.add(MemoryMessage.builder()
                    .role(MemoryRole.SYSTEM)
                    .content("[Compressed History]\n" + summarize(memoryId, compressible))
                    .build());
        }
        result.addAll(pinned);
        result.addAll(recent);
        return result;
    }

    private String summarize(String memoryId, List<MemoryMessage> compressible) {
        String conversation = compressible.stream()
                .map(message -> message.role() + ": " + abbreviate(message.content(), 300))
                .collect(Collectors.joining("\n"));
        try {
            return aiGateway.chat(AiPromptRequest.builder()
                    .sceneCode("MEMORY_COMPACTION")
                    .sessionId(memoryId)
                    .systemPrompt("Compress conversation history into concise Chinese bullet points, preserving facts and decisions.")
                    .userPrompt(conversation)
                    .build()).content();
        } catch (Exception ex) {
            return compressible.stream()
                    .limit(3)
                    .map(message -> message.role() + ": " + abbreviate(message.content(), 200))
                    .collect(Collectors.joining("\n", "[Truncated] ", ""));
        }
    }

    private List<MemoryMessage> load(String memoryId) {
        List<MemoryMessage> messages = store.get(memoryId);
        if (messages != null) {
            return messages;
        }
        List<MemoryMessage> restored = restore(memoryId);
        if (!restored.isEmpty()) {
            store.put(memoryId, List.copyOf(restored));
        }
        return restored;
    }

    private void persist(String memoryId, List<MemoryMessage> messages) {
        StringRedisTemplate redisTemplate = redisTemplate();
        if (redisTemplate == null || memoryId == null) {
            return;
        }
        try {
            List<Map<String, Object>> rows = messages.stream()
                    .map(message -> Map.<String, Object>of(
                            "role", message.role() == null ? MemoryRole.SYSTEM.name() : message.role().name(),
                            "content", message.content() == null ? "" : message.content(),
                            "timestamp", message.timestamp() == null ? Instant.now().toString() : message.timestamp().toString(),
                            "metadata", JSON.toJSONString(message.metadata() == null ? Map.of() : message.metadata())
                    ))
                    .toList();
            redisTemplate.opsForValue().set(redisKey(memoryId), JSON.toJSONString(rows), REDIS_TTL);
        } catch (Exception ignored) {
            // Keep in-memory memory available when Redis is down.
        }
    }

    @SuppressWarnings("unchecked")
    private List<MemoryMessage> restore(String memoryId) {
        StringRedisTemplate redisTemplate = redisTemplate();
        if (redisTemplate == null || memoryId == null) {
            return List.of();
        }
        try {
            String json = redisTemplate.opsForValue().get(redisKey(memoryId));
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return JSON.parseArray(json, JSONObject.class).stream()
                    .map(row -> MemoryMessage.builder()
                            .role(parseRole(row.getString("role")))
                            .content(row.getString("content"))
                            .timestamp(parseInstant(row.getString("timestamp")))
                            .metadata(JSON.parseObject(row.getString("metadata"), Map.class))
                            .build())
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private MemoryRole parseRole(String value) {
        try {
            return value == null || value.isBlank() ? MemoryRole.SYSTEM : MemoryRole.valueOf(value);
        } catch (Exception ex) {
            return MemoryRole.SYSTEM;
        }
    }

    private Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.now() : Instant.parse(value);
        } catch (Exception ex) {
            return Instant.now();
        }
    }

    private StringRedisTemplate redisTemplate() {
        return redisTemplateProvider == null ? null : redisTemplateProvider.getIfAvailable();
    }

    private String redisKey(String memoryId) {
        return REDIS_KEY_PREFIX + memoryId;
    }

    private String extractDecisionSummary(MemoryMessage message) {
        return abbreviate(message == null ? "" : message.content(), 100);
    }

    private String abbreviate(String content, int max) {
        if (content == null) {
            return "";
        }
        return content.length() <= max ? content : content.substring(0, max) + "...";
    }
}
