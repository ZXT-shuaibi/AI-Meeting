# Agentic ThreadLocal And Observability Notes

## JobSpark Reference

JobSpark reproduced a LangChain4j Agentic issue where direct Agent invocation with native listeners/tools could miss LangChain4j's managed ThreadLocal. The problematic pattern is:

- Build an Agentic agent.
- Register native `AgentListener`.
- Invoke the agent directly from business code, not as a sub-agent inside a managed agentic system.
- Tool/listener callback expects `LangChain4jManaged.current()` but it is null.

JobSpark also had an Agent listener architecture:

- `AgentListenerFactory`
- `PersistableAgentListener`
- Spring events for invocation and tool execution
- Redis hot storage and MySQL cold storage
- Per-agent-type listener enablement

## AI-Meeting Fusion Decision

AI-Meeting does not patch third-party LangChain4j source. Instead it uses a boundary policy:

- `LangChain4jAgentAdapter` wraps Agent calls and publishes unified traces through `AiTracePublisher`.
- Invocation reflection failures are unwrapped before publishing failure events, so real root causes are visible.
- `LangChain4jAgenticSafetyPolicy` disables native Agentic listener wiring by default.
- `xunzhi-agent.langchain4j.agentic-native-listeners-enabled` defaults to `false`.
- If someone turns that setting on, Agentic configuration fails fast with a ThreadLocal risk message.

## Observability Mapping

Unified AI events:

- `AiInvocationStartedEvent`
- `AiInvocationCompletedEvent`
- `AiInvocationFailedEvent`
- `AiToolExecutionEvent`

Storage:

- Redis keeps hot traces and recent session trace lists.
- MySQL keeps cold invocation traces, tool executions, and session stats.
- Completed/failed events update the existing trace id instead of inserting fragmented rows.

Legacy AI coverage:

- `UniversalAiChatHandler` publishes `LEGACY_AI_CHAT` invocation traces for Spring AI compatible OpenAI/DeepSeek/Spark chat.
- `AgentMessageServiceImpl` publishes `LEGACY_XINGCHEN_AGENT_CHAT` invocation traces around XingChen workflow streaming chat.
- `AgentFileAssetServiceImpl` publishes `LEGACY_XINGCHEN_FILE_UPLOAD` tool traces around XingChen file uploads.
- `XunfeiAudioService` publishes `LEGACY_XUNFEI_REALTIME_ASR` invocation traces around realtime ASR futures.
- `XunfeiLongTextTtsService` publishes `LEGACY_XUNFEI_TTS_CREATE` and `LEGACY_XUNFEI_TTS_QUERY` invocation traces around long-text TTS task operations.

## Safe Extension Rule

If native LangChain4j listeners are needed later:

1. Add a minimal reproduction test equivalent to JobSpark's ThreadLocal NPE case.
2. Prove the LangChain4j version or wrapper initializes managed scope for direct Agent calls.
3. Keep `AiTracePublisher` as the canonical persistence path.
4. Enable native listener only behind explicit profile/config.
5. Never let native listener failure break AI-Meeting interview execution.

## Current Limitation

Observability is unified for career Agent/tool events, the main legacy chat/file-upload paths, and Xunfei media service entrypoints. ASR raw packet payloads remain local service logs unless a future packet-level trace persistence requirement is added.
