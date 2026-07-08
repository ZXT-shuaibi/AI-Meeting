package com.hewei.hzyjy.xunzhi.career.memory;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DecisionIndex {

    private final Map<Object, List<DecisionEntry>> index = new ConcurrentHashMap<>();

    public void record(Object memoryId, int messageIndex, String summary) {
        index.computeIfAbsent(memoryId, key -> new ArrayList<>())
                .add(new DecisionEntry(messageIndex, summary, Instant.now()));
    }

    public List<DecisionEntry> getDecisions(Object memoryId) {
        return List.copyOf(index.getOrDefault(memoryId, List.of()));
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
        index.remove(memoryId);
    }
}
