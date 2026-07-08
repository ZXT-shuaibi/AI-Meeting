package com.hewei.hzyjy.xunzhi.career.resume.rag;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bm25ScorerTest {

    @Test
    void ranksDocumentsByKeywordRelevance() {
        List<String> documents = List.of(
                "Spring Boot Redis MySQL interview platform",
                "Vue React CSS animation portfolio",
                "Spring AI LangChain4j Redis RAG resume optimization"
        );

        Map<String, Double> scores = Bm25Scorer.score(documents, "Spring Redis RAG");

        String top = scores.entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .orElseThrow()
                .getKey();
        assertEquals("Spring AI LangChain4j Redis RAG resume optimization", top);
        assertTrue(scores.get(top) > scores.get("Vue React CSS animation portfolio"));
    }

    @Test
    void returnsEmptyScoreWhenQueryIsBlank() {
        assertTrue(Bm25Scorer.score(List.of("Spring AI"), " ").isEmpty());
    }
}
