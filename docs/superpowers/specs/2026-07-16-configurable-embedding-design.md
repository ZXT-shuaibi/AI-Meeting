# Configurable Embedding Design

## Goal

Enable the fused career module to call any OpenAI-compatible embedding API without changing the MiMo chat configuration. Provider changes take effect after an application restart.

## Configuration

The application exposes `xunzhi-agent.embedding` through environment variables:

- `XUNZHI_EMBEDDING_ENABLED`
- `XUNZHI_EMBEDDING_BASE_URL`
- `XUNZHI_EMBEDDING_API_KEY`
- `XUNZHI_EMBEDDING_MODEL`
- `XUNZHI_EMBEDDING_DIMENSIONS`
- `XUNZHI_EMBEDDING_TIMEOUT_SECONDS`

All four connection settings must be present before a remote embedding client is created. Missing or disabled configuration preserves the existing local hashing fallback.

## Runtime Behavior

An explicit LangChain4j `EmbeddingModel` bean is created from the configuration. `LangChain4jEmbeddingAdapter` uses it when available. It retains the current fallback when the client is unavailable or a provider request fails.

The embedding configuration is independent of `spring.ai.openai` and `xunzhi-agent.langchain4j`, allowing MiMo to remain the chat provider.

## Vector Stores

The configured embedding dimension defaults to 1024. When Qdrant is enabled, its collection vector size must match the selected model output dimension. Qdrant remains disabled by default.

## Verification

Tests cover configuration validation, explicit embedding-client creation, remote-result usage, and fallback behavior when configuration is absent.
