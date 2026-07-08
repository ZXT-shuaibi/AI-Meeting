package com.hewei.hzyjy.xunzhi.career.memory;

import com.hewei.hzyjy.xunzhi.career.ai.AiGateway;
import com.hewei.hzyjy.xunzhi.career.ai.AiPromptRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class HybridCompactingChatMemory {

    private static final int DEFAULT_COMPACT_THRESHOLD = 30;
    private static final int DEFAULT_RECENT_MESSAGE_COUNT = 6;

    private final AiGateway aiGateway;
    private final ImportanceScorer importanceScorer;
    private final DecisionIndex decisionIndex;
    private final Map<String, List<MemoryMessage>> store = new ConcurrentHashMap<>();

    public void add(String memoryId, MemoryMessage message) {
        List<MemoryMessage> messages = new ArrayList<>(store.getOrDefault(memoryId, List.of()));
        messages.add(message);
        MemoryImportance importance = importanceScorer.score(message, messages);
        if (importance == MemoryImportance.HIGH) {
            decisionIndex.record(memoryId, messages.size() - 1, extractDecisionSummary(message));
        }
        if (messages.size() > DEFAULT_COMPACT_THRESHOLD) {
            messages = compact(memoryId, messages, DEFAULT_RECENT_MESSAGE_COUNT);
        }
        store.put(memoryId, List.copyOf(messages));
    }

    public CompactedMemoryView view(String memoryId, int recentDecisionLimit) {
        List<MemoryMessage> messages = List.copyOf(store.getOrDefault(memoryId, List.of()));
        return CompactedMemoryView.builder()
                .memoryId(memoryId)
                .messages(messages)
                .decisions(decisionIndex.getRecentDecisions(memoryId, recentDecisionLimit))
                .decisionContext(decisionIndex.formatDecisionContext(memoryId, recentDecisionLimit))
                .build();
    }

    public List<MemoryMessage> messages(String memoryId) {
        return List.copyOf(store.getOrDefault(memoryId, List.of()));
    }

    public void clear(String memoryId) {
        store.remove(memoryId);
        decisionIndex.clear(memoryId);
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
