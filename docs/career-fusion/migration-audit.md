# AI-Meeting x JobSpark-Resume Migration Audit

## Runtime Boundary

- AI-Meeting remains the main project and keeps Spring AI as the primary model-call runtime.
- Career fusion code is isolated under `com.hewei.hzyjy.xunzhi.career`.
- LangChain4j is introduced behind `AgentRuntimeGateway` / `EmbeddingGateway` adapters and optional dependencies.
- Sa-Token remains the auth system. JobSpark JWT/Spring Security is not migrated.

## Migrated Highlights

### P0 Resume + RAG

- Structured resume aggregate `CvBO` and nested resume BOs are available.
- Resume upload stores structured data through `ResumeStore`.
- MySQL-first `MySqlResumeStore` is backed by in-memory fallback.
- RAG chunking uses overview / summary / skills / experience / project / education chunks.
- Retrieval keeps HyDE, multi-query, hierarchical coarse recall, vector + BM25 fine recall, RRF fusion, context augmentation, DashScope rerank gateway, Redis cache, and BM25 fallback.
- Resume chunks and fallback vectors are persisted in `career_resume_chunk` so local vector search can warm up after restart.

### P1 Judge-Executor CV Optimization

- `CvOptimizationOrchestrator` implements the reviewer-tailor loop.
- Exit rule: `score > 0.8`, max iteration default 3.
- LLM/parse failure returns the latest usable result.
- `AiCvReviewer` parses structured LLM score/feedback before heuristic fallback.
- `AiScoredCvTailor` parses structured title/summary/advice before advice-append fallback.

### P2 Plan-Execute-Reflect Interview Planning

- `InterviewPlanningService` adds JD alignment, stage coordination, first-question generation, and reflection routing.
- `ReflectionResult` returns `PROBE / NEXT / STAGE_FINISH / FINISH` decisions.
- It does not replace AI-Meeting's existing interview execution pipeline, locks, idempotency, snapshots, or Single-flight.

### P3 Hybrid Memory + DecisionIndex

- `HybridCompactingChatMemory`, `DecisionIndex`, rule-based importance scoring, message roles, and compacted memory view are migrated.
- Planning, optimization, upload, embedding, and reflection add key decisions/messages to memory.
- Reflection and JD alignment inject recent decision context into prompts.

### P4 Observability

- Unified AI events exist for started/completed/failed/tool execution.
- Redis stores hot traces and session trace lists.
- MySQL stores cold invocation traces and tool executions.
- Completed/failed events update the same `trace_id` instead of inserting fragmented rows.
- Session aggregate stats are stored in `ai_agent_session_stats`.
- `@EnableAsync` is enabled so event listeners can run asynchronously.

## High-Availability Status

- Resume data, job match tasks, RAG chunks, fallback vectors, traces, and stats have MySQL persistence paths.
- Redis is used for hot observability and RAG query cache.
- Local in-memory fallbacks keep demos usable when MySQL/Qdrant/LLM dependencies are down, but production must apply the SQL files.
- Qdrant is currently an adapter seam with MySQL vector fallback. A real Qdrant client implementation is still required before claiming external-vector-store HA.
- LangChain4j concrete Agentic beans are not hard-wired; the migration keeps the boundary and optional dependencies, but concrete agent registration needs profile-specific configuration before full LangChain4j Agentic runtime verification.

## Required Bootstrap

Run these before production use:

1. `admin/src/main/resources/sql/career_resume.sql`
2. `admin/src/main/resources/sql/ai_observability.sql`
3. Configure `xunzhi-agent.agent-binding.*` to existing `agent_properties.ai_name` values or seed matching agent rows.
4. Configure DashScope/Qdrant env vars if external rerank/vector store is required.

## Verification Notes

Attempted Maven verification:

```powershell
mvn.cmd -pl admin -DskipTests compile
mvn.cmd -pl admin "-Dtest=AiCvReviewerTest,InterviewPlanningServiceTest,Bm25ScorerTest,DecisionIndexTest,CvOptimizationOrchestratorTest" test
```

Both currently fail before Java compilation because Maven cannot resolve `maven-enforcer-plugin:3.5.0` from Maven Central due local Java PKIX certificate trust failure.
