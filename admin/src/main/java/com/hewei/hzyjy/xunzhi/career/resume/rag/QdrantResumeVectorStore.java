package com.hewei.hzyjy.xunzhi.career.resume.rag;

import com.hewei.hzyjy.xunzhi.career.config.XunzhiLangChain4jProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_CHUNK_TYPE;
import static com.hewei.hzyjy.xunzhi.career.resume.rag.ResumeRagConstants.META_RESUME_ID;

@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantResumeVectorStore {

    private static final long FAILURE_BACKOFF_MILLIS = 30000L;

    private final XunzhiLangChain4jProperties properties;
    private volatile RestTemplate restTemplate;
    private volatile boolean collectionEnsured;
    private volatile long unavailableUntilMillis;

    public void addAll(List<ResumeVectorDocument> incomingDocuments) {
        if (!available() || incomingDocuments == null || incomingDocuments.isEmpty()) {
            return;
        }
        validateVectors(incomingDocuments);
        if (!ensureCollection()) {
            return;
        }
        Set<String> resumeIds = incomingDocuments.stream()
                .map(document -> document.metadata() == null ? null : document.metadata().get(META_RESUME_ID))
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toSet());
        deleteByResumeIds(resumeIds);
        List<Map<String, Object>> points = incomingDocuments.stream()
                .map(this::toPoint)
                .toList();
        Map<String, Object> body = Map.of("points", points);
        exchange(HttpMethod.PUT, "/collections/" + collectionName() + "/points?wait=true", body);
    }

    public List<ResumeVectorMatch> search(float[] queryVector, Set<String> chunkTypes, Set<String> resumeIds, double minScore, int limit) {
        return search(queryVector, chunkTypes, resumeIds, Map.of(), minScore, limit);
    }

    @SuppressWarnings("unchecked")
    public List<ResumeVectorMatch> search(
            float[] queryVector,
            Set<String> chunkTypes,
            Set<String> resumeIds,
            Map<String, String> metadataFilters,
            double minScore,
            int limit) {
        if (!available() || queryVector == null || queryVector.length == 0 || limit <= 0) {
            return List.of();
        }
        validateVectorDimension(queryVector);
        if (!ensureCollection()) {
            return List.of();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("vector", toVector(queryVector));
        body.put("limit", limit);
        body.put("score_threshold", minScore);
        body.put("with_payload", true);
        body.put("with_vector", true);
        Map<String, Object> filter = buildFilter(chunkTypes, resumeIds, metadataFilters);
        if (!filter.isEmpty()) {
            body.put("filter", filter);
        }
        ResponseEntity<Map> response = exchange(HttpMethod.POST, "/collections/" + collectionName() + "/points/search", body);
        Object result = response.getBody() == null ? null : response.getBody().get("result");
        if (!(result instanceof List<?> rows)) {
            return List.of();
        }
        List<ResumeVectorMatch> matches = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> payload = map.get("payload") instanceof Map<?, ?> payloadMap
                    ? (Map<String, Object>) payloadMap
                    : Map.of();
            String text = String.valueOf(payload.getOrDefault(payloadTextKey(), ""));
            Map<String, String> metadata = new HashMap<>();
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                if (!payloadTextKey().equals(entry.getKey()) && entry.getValue() != null) {
                    metadata.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            float[] vector = map.get("vector") instanceof List<?> vectorValues ? toFloatArray(vectorValues) : new float[0];
            ResumeVectorDocument document = ResumeVectorDocument.builder()
                    .id(String.valueOf(map.get("id")))
                    .text(text)
                    .vector(vector)
                    .metadata(metadata)
                    .build();
            double score = parseDouble(map.get("score"));
            matches.add(ResumeVectorMatch.builder().document(document).score(score).build());
        }
        return matches.stream()
                .sorted(Comparator.comparingDouble(ResumeVectorMatch::score).reversed())
                .limit(limit)
                .toList();
    }

    public boolean available() {
        XunzhiLangChain4jProperties.Qdrant qdrant = properties.getQdrant();
        return qdrant != null
                && qdrant.isEnabled()
                && System.currentTimeMillis() >= unavailableUntilMillis
                && StringUtils.hasText(qdrant.getHost())
                && StringUtils.hasText(qdrant.getCollectionName());
    }

    private void validateVectors(List<ResumeVectorDocument> documents) {
        documents.forEach(document -> validateVectorDimension(document.vector()));
    }

    private void validateVectorDimension(float[] vector) {
        int expectedDimension = properties.getQdrant().getVectorSize();
        int actualDimension = vector == null ? 0 : vector.length;
        if (actualDimension != expectedDimension) {
            throw new IllegalArgumentException(
                    "Embedding vector dimension " + actualDimension + " does not match Qdrant collection dimension " + expectedDimension
            );
        }
    }

    private void deleteByResumeIds(Set<String> resumeIds) {
        if (resumeIds == null || resumeIds.isEmpty()) {
            return;
        }
        exchange(
                HttpMethod.POST,
                "/collections/" + collectionName() + "/points/delete?wait=true",
                Map.of("filter", buildFilter(Set.of(), resumeIds, Map.of()))
        );
    }
    private Map<String, Object> toPoint(ResumeVectorDocument document) {
        Map<String, Object> payload = new HashMap<>(document.metadata() == null ? Map.of() : document.metadata());
        payload.put(payloadTextKey(), document.text());
        return Map.of(
                "id", document.id(),
                "vector", toVector(document.vector()),
                "payload", payload
        );
    }

    private Map<String, Object> buildFilter(Set<String> chunkTypes, Set<String> resumeIds, Map<String, String> metadataFilters) {
        List<Map<String, Object>> must = new ArrayList<>();
        addAnyFilter(must, META_CHUNK_TYPE, chunkTypes);
        addAnyFilter(must, META_RESUME_ID, resumeIds);
        if (metadataFilters != null) {
            metadataFilters.forEach((key, value) -> {
                if (StringUtils.hasText(key) && value != null) {
                    must.add(Map.of("key", key, "match", Map.of("value", value)));
                }
            });
        }
        return must.isEmpty() ? Map.of() : Map.of("must", must);
    }

    private void addAnyFilter(List<Map<String, Object>> must, String key, Set<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        if (values.size() == 1) {
            must.add(Map.of("key", key, "match", Map.of("value", values.iterator().next())));
        } else {
            must.add(Map.of("key", key, "match", Map.of("any", values.stream().toList())));
        }
    }

    private boolean ensureCollection() {
        if (collectionEnsured) {
            return true;
        }
        synchronized (this) {
            if (collectionEnsured) {
                return true;
            }
            try {
                Map<String, Object> body = Map.of(
                        "vectors", Map.of(
                                "size", properties.getQdrant().getVectorSize(),
                                "distance", "Cosine"
                        )
                );
                exchange(HttpMethod.PUT, "/collections/" + collectionName(), body);
                collectionEnsured = true;
                unavailableUntilMillis = 0L;
                return true;
            } catch (Exception ex) {
                unavailableUntilMillis = System.currentTimeMillis() + FAILURE_BACKOFF_MILLIS;
                log.debug("Qdrant collection ensure failed; fallback store remains active. collection={}", collectionName(), ex);
                return false;
            }
        }
    }

    private ResponseEntity<Map> exchange(HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String apiKey = properties.getQdrant().getApiKey();
        if (StringUtils.hasText(apiKey)) {
            headers.set("api-key", apiKey);
        }
        try {
            return restTemplate().exchange(baseUrl() + path, method, new HttpEntity<>(body, headers), Map.class);
        } catch (RestClientException ex) {
            unavailableUntilMillis = System.currentTimeMillis() + FAILURE_BACKOFF_MILLIS;
            throw new IllegalStateException("Qdrant REST call failed: " + path, ex);
        }
    }

    private RestTemplate restTemplate() {
        RestTemplate current = restTemplate;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (restTemplate == null) {
                SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                int timeout = Math.max(1000, properties.getQdrant().getTimeoutMillis());
                factory.setConnectTimeout(timeout);
                factory.setReadTimeout(timeout);
                restTemplate = new RestTemplate(factory);
            }
            return restTemplate;
        }
    }

    private String baseUrl() {
        XunzhiLangChain4jProperties.Qdrant qdrant = properties.getQdrant();
        String scheme = qdrant.isUseTls() ? "https" : "http";
        return scheme + "://" + qdrant.getHost() + ":" + qdrant.getPort();
    }

    private String collectionName() {
        return properties.getQdrant().getCollectionName();
    }

    private String payloadTextKey() {
        return properties.getQdrant().getPayloadTextKey();
    }

    private List<Float> toVector(float[] vector) {
        List<Float> values = new ArrayList<>();
        if (vector != null) {
            for (float value : vector) {
                values.add(value);
            }
        }
        return values;
    }

    private float[] toFloatArray(List<?> values) {
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            vector[i] = value instanceof Number number ? number.floatValue() : 0.0F;
        }
        return vector;
    }

    private double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0.0 : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }
}
