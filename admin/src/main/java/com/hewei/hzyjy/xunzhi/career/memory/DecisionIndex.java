package com.hewei.hzyjy.xunzhi.career.memory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DecisionIndex {

    private static final String REDIS_KEY_PREFIX = "xunzhi-agent:career:memory:decision:";
    private static final Duration REDIS_TTL = Duration.ofDays(7);

    private final Cache<Object, List<DecisionEntry>> index;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final MysqlDecisionStore coldStore;

    public DecisionIndex() {
        this(null, null, null);
    }

    public DecisionIndex(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this(redisTemplateProvider, null, null);
    }

    @Autowired
    public DecisionIndex(ObjectProvider<StringRedisTemplate> redisTemplateProvider, MysqlDecisionStore coldStore) {
        this(redisTemplateProvider, coldStore, null);
    }

    DecisionIndex(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            MysqlDecisionStore coldStore,
            Cache<Object, List<DecisionEntry>> index) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.coldStore = coldStore;
        this.index = index == null ? MemoryHotCache.create() : index;
    }

    public void record(Object memoryId, int messageIndex, String summary) {
        List<DecisionEntry> decisions = new ArrayList<>(getDecisions(memoryId));
        decisions.add(new DecisionEntry(messageIndex, summary, Instant.now()));
        List<DecisionEntry> snapshot = List.copyOf(decisions);
        index.put(memoryId, snapshot);
        persistRedis(memoryId, snapshot);
        persistCold(memoryId, snapshot);
    }

    public List<DecisionEntry> getDecisions(Object memoryId) {
        List<DecisionEntry> decisions = index.getIfPresent(memoryId);
        if (decisions != null) {
            return List.copyOf(decisions);
        }
        List<DecisionEntry> restored = restore(memoryId);
        if (!restored.isEmpty()) {
            index.put(memoryId, List.copyOf(restored));
        }
        return restored;
    }

    public List<DecisionEntry> getRecentDecisions(Object memoryId, int limit) {
        List<DecisionEntry> decisions = getDecisions(memoryId);
        if (limit <= 0 || decisions.isEmpty()) {
            return List.of();
        }
        if (decisions.size() <= limit) {
            return decisions;
        }
        return decisions.subList(decisions.size() - limit, decisions.size());
    }

    public String formatDecisionContext(Object memoryId, int limit) {
        List<DecisionEntry> decisions = getRecentDecisions(memoryId, limit);
        if (decisions.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder("[Historical Key Decisions]\n");
        for (int i = 0; i < decisions.size(); i++) {
            context.append("- [").append(i + 1).append("] ")
                    .append(decisions.get(i).summary())
                    .append("\n");
        }
        return context.toString();
    }

    public void clear(Object memoryId) {
        index.invalidate(memoryId);
        StringRedisTemplate redisTemplate = redisTemplate();
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(redisKey(memoryId));
            } catch (Exception ignored) {
                // Redis is a recovery layer only; clearing in-memory state is sufficient for local correctness.
            }
        }
        persistCold(memoryId, List.of());
    }

    private void persistRedis(Object memoryId, List<DecisionEntry> decisions) {
        StringRedisTemplate redisTemplate = redisTemplate();
        if (redisTemplate == null || memoryId == null) {
            return;
        }
        try {
            List<Map<String, Object>> rows = decisions.stream()
                    .map(entry -> Map.<String, Object>of(
                            "messageIndex", entry.messageIndex(),
                            "summary", entry.summary() == null ? "" : entry.summary(),
                            "timestamp", entry.timestamp() == null ? Instant.now().toString() : entry.timestamp().toString()
                    ))
                    .toList();
            redisTemplate.opsForValue().set(redisKey(memoryId), JSON.toJSONString(rows), REDIS_TTL);
        } catch (Exception ignored) {
            // Keep the hot in-memory index available when Redis is down.
        }
    }

    private List<DecisionEntry> restore(Object memoryId) {
        if (memoryId == null) {
            return List.of();
        }
        StringRedisTemplate redisTemplate = redisTemplate();
        if (redisTemplate == null) {
            return restoreCold(memoryId);
        }
        try {
            String json = redisTemplate.opsForValue().get(redisKey(memoryId));
            if (json == null || json.isBlank()) {
                return restoreCold(memoryId);
            }
            return JSON.parseArray(json, JSONObject.class).stream()
                    .map(row -> new DecisionEntry(
                            row.getIntValue("messageIndex"),
                            row.getString("summary"),
                            parseInstant(row.getString("timestamp"))
                    ))
                    .toList();
        } catch (Exception ignored) {
            return restoreCold(memoryId);
        }
    }

    private void persistCold(Object memoryId, List<DecisionEntry> decisions) {
        if (coldStore != null) {
            coldStore.replace(memoryId, decisions);
        }
    }

    private List<DecisionEntry> restoreCold(Object memoryId) {
        return coldStore == null ? List.of() : coldStore.load(memoryId);
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

    private String redisKey(Object memoryId) {
        return REDIS_KEY_PREFIX + memoryId;
    }
}
