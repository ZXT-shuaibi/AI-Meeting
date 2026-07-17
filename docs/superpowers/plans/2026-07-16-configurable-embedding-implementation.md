# Configurable Embedding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create an independent OpenAI-compatible embedding client that can be configured after restart without changing MiMo chat settings.

**Architecture:** A dedicated configuration-properties class and Spring configuration create an `EmbeddingModel` only when all required embedding connection settings are present. The existing career adapter consumes that typed model and retains its hashing-vector fallback when no client is present or a request fails.

**Tech Stack:** Spring Boot configuration properties, LangChain4j OpenAI embedding model, JUnit 5, AssertJ.

---

### Task 1: Establish adapter behavior with failing tests

**Files:**
- Modify: `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/ai/LangChain4jEmbeddingAdapterTest.java`
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/ai/LangChain4jEmbeddingAdapter.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void usesRegisteredEmbeddingModelForRemoteVectors() {
    StaticApplicationContext context = new StaticApplicationContext();
    context.getBeanFactory().registerSingleton("embeddingModel", new FixedEmbeddingModel(new float[]{0.6f, 0.8f}));
    LangChain4jEmbeddingAdapter adapter = new LangChain4jEmbeddingAdapter(context, new XunzhiLangChain4jProperties());

    assertArrayEquals(new float[]{0.6f, 0.8f}, adapter.embed("resume"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd -pl admin -Dtest=LangChain4jEmbeddingAdapterTest test`

Expected: FAIL because the adapter invokes a reflective `embedAll(List<String>)` call instead of the LangChain4j `EmbeddingModel` API.

- [ ] **Step 3: Write minimal implementation**

```java
EmbeddingModel embeddingModel = applicationContext.getBeanProvider(EmbeddingModel.class).getIfAvailable();
if (embeddingModel != null) {
    return embeddingModel.embedAll(texts.stream().map(TextSegment::from).toList())
            .content().stream().map(Embedding::vector).toList();
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw.cmd -pl admin -Dtest=LangChain4jEmbeddingAdapterTest test`

Expected: PASS.

### Task 2: Add independent OpenAI-compatible embedding configuration

**Files:**
- Create: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/config/EmbeddingProperties.java`
- Create: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/config/OpenAiCompatibleEmbeddingConfiguration.java`
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/config/CareerConfiguration.java`
- Modify: `admin/src/main/resources/application.yaml`
- Create: `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/config/OpenAiCompatibleEmbeddingConfigurationTest.java`

- [ ] **Step 1: Write failing context tests**

```java
@Test
void createsEmbeddingModelOnlyWhenAllRequiredPropertiesArePresent() {
    contextRunner.withPropertyValues(
            "xunzhi-agent.embedding.enabled=true",
            "xunzhi-agent.embedding.base-url=https://embedding.example/v1",
            "xunzhi-agent.embedding.api-key=test-key",
            "xunzhi-agent.embedding.model=test-embedding"
    ).run(context -> assertThat(context).hasSingleBean(EmbeddingModel.class));
}

@Test
void omitsEmbeddingModelWhenApiKeyIsMissing() {
    contextRunner.withPropertyValues("xunzhi-agent.embedding.enabled=true")
            .run(context -> assertThat(context).doesNotHaveBean(EmbeddingModel.class));
}
```

- [ ] **Step 2: Run the configuration test to verify it fails**

Run: `./mvnw.cmd -pl admin -Dtest=OpenAiCompatibleEmbeddingConfigurationTest test`

Expected: FAIL because no configuration class or properties binding exists.

- [ ] **Step 3: Implement the properties and conditional bean**

```java
return OpenAiEmbeddingModel.builder()
        .baseUrl(properties.getBaseUrl())
        .apiKey(properties.getApiKey())
        .modelName(properties.getModel())
        .dimensions(properties.getDimensions())
        .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
        .maxRetries(1)
        .build();
```

The condition must require `enabled=true` and nonblank `base-url`, `api-key`, and `model` values.

- [ ] **Step 4: Add environment-variable defaults**

```yaml
xunzhi-agent:
  embedding:
    enabled: ${XUNZHI_EMBEDDING_ENABLED:false}
    base-url: ${XUNZHI_EMBEDDING_BASE_URL:}
    api-key: ${XUNZHI_EMBEDDING_API_KEY:}
    model: ${XUNZHI_EMBEDDING_MODEL:}
    dimensions: ${XUNZHI_EMBEDDING_DIMENSIONS:1024}
    timeout-seconds: ${XUNZHI_EMBEDDING_TIMEOUT_SECONDS:15}
```

- [ ] **Step 5: Run the focused tests**

Run: `./mvnw.cmd -pl admin -Dtest=LangChain4jEmbeddingAdapterTest,OpenAiCompatibleEmbeddingConfigurationTest test`

Expected: PASS.

### Task 3: Verify module compatibility

**Files:**
- Modify: `AI-Meeting/.env.example`

- [ ] **Step 1: Document optional embedding environment variables**

```dotenv
XUNZHI_EMBEDDING_ENABLED=false
XUNZHI_EMBEDDING_BASE_URL=
XUNZHI_EMBEDDING_API_KEY=
XUNZHI_EMBEDDING_MODEL=
XUNZHI_EMBEDDING_DIMENSIONS=1024
XUNZHI_EMBEDDING_TIMEOUT_SECONDS=15
```

- [ ] **Step 2: Run relevant unit tests and compile**

Run: `./mvnw.cmd -pl admin -Dtest=LangChain4jEmbeddingAdapterTest,OpenAiCompatibleEmbeddingConfigurationTest test`

Run: `./mvnw.cmd -pl admin -DskipTests compile`

Expected: both commands succeed.
