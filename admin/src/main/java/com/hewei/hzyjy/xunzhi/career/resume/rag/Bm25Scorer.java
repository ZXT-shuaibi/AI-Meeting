package com.hewei.hzyjy.xunzhi.career.resume.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Bm25Scorer {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private Bm25Scorer() {
    }

    public static Map<String, Double> score(List<String> documents, String query) {
        if (documents == null || documents.isEmpty() || query == null || query.isBlank()) {
            return Collections.emptyMap();
        }

        List<List<String>> docTokens = documents.stream()
                .map(Bm25Scorer::tokenize)
                .toList();
        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return Collections.emptyMap();
        }

        double avgDocLen = docTokens.stream().mapToInt(List::size).average().orElse(0.0);
        int docCount = documents.size();
        Map<String, Double> idfMap = new HashMap<>();
        for (String term : queryTokens) {
            int containingDocs = 0;
            for (List<String> tokens : docTokens) {
                if (tokens.contains(term)) {
                    containingDocs++;
                }
            }
            idfMap.put(term, Math.log((docCount - containingDocs + 0.5) / (containingDocs + 0.5) + 1.0));
        }

        Map<String, Double> scores = new LinkedHashMap<>();
        for (int i = 0; i < documents.size(); i++) {
            List<String> tokens = docTokens.get(i);
            int docLen = tokens.size();
            double score = 0.0;
            for (String term : queryTokens) {
                long tf = tokens.stream().filter(term::equals).count();
                double denom = tf + K1 * (1 - B + B * docLen / Math.max(avgDocLen, 1.0));
                if (denom > 0) {
                    score += idfMap.getOrDefault(term, 0.0) * (tf * (K1 + 1.0)) / denom;
                }
            }
            scores.put(documents.get(i), score);
        }
        return scores;
    }

    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char c : text.toLowerCase().toCharArray()) {
            if (Character.isLetterOrDigit(c) || isChinese(c)) {
                current.append(c);
            } else if (current.length() > 0) {
                tokens.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static boolean isChinese(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A;
    }
}
