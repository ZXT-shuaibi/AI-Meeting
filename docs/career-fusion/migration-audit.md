# AI-Meeting x JobSpark-Resume Migration Audit

## Runtime Boundary

- AI-Meeting remains the main project and keeps Spring AI as the primary model-call runtime.
- Career fusion code is isolated under `com.hewei.hzyjy.xunzhi.career`.
- LangChain4j is behind `AgentRuntimeGateway` / `EmbeddingGateway`; default build keeps external LangChain4j dependencies in the optional `career-external-ai` profile.
- Default runtime now has local Agent Facade beans named `CvReviewer`, `ScoredCvTailor`, `JDAlignmentAgent`, `InterviewCoordinatorAgent`, `InterviewOrchestratorService`, and `InterviewReflectorAgent`, so `LangChain4jAgentAdapter` has a real invocation path even without external LangChain4j jars.
- Sa-Token remains the auth system. JobSpark JWT/Spring Security is not migrated.

## Migrated Highlights

### P0 Resume + RAG

- Structured resume aggregate `CvBO` and nested resume BOs are available.
- Resume upload stores structured data through `ResumeStore` and triggers best-effort embedding immediately after save.
- MySQL-first `MySqlResumeStore` is backed by in-memory fallback and enforces owner lookup through `findByIdAndUserId`.
- RAG chunking uses overview / summary / skills / experience / project / education chunks with `user_id` and `resume_id` metadata.
- Retrieval keeps HyDE, multi-query, hierarchical coarse recall, vector + BM25 fine recall, RRF fusion, context augmentation, DashScope rerank gateway, Redis cache, and BM25 fallback.
- Resume chunks and fallback vectors are persisted in `career_resume_chunk` so local vector search can warm up after restart.
- Qdrant REST adapter is implemented and disabled by default; when enabled it deletes existing points by `resume_id` before upsert to avoid stale optimized-resume chunks.

### P1 Judge-Executor CV Optimization

- `CvOptimizationOrchestrator` implements the reviewer-tailor loop.
- Exit rule: `score > 0.8`, max iteration default 3.
- On max iterations, the orchestrator returns the latest reviewed CV, not an unreviewed post-tailor version.
- LLM/parse failure returns the latest usable result.
- `AiCvReviewer` and `AiScoredCvTailor` try `AgentRuntimeGateway` first, then fall back to Spring AI/heuristics.

### P2 Plan-Execute-Reflect Interview Planning

- `InterviewPlanningService` adds JD alignment, stage coordination, first-question generation, and reflection routing.
- `ReflectionResult` returns `PROBE / NEXT / STAGE_FINISH / FINISH` decisions.
- Internal memory IDs include user scope for controller paths, avoiding cross-user context sharing when session IDs collide.
- It does not replace AI-Meeting's existing interview execution pipeline, locks, idempotency, snapshots, or Single-flight.

### P3 Hybrid Memory + DecisionIndex

- `HybridCompactingChatMemory`, `DecisionIndex`, rule-based importance scoring, message roles, and compacted memory view are migrated.
- HIGH messages stay pinned, LOW messages are summarized, recent messages are retained, and Redis read/write failure falls back to local memory.
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
- `career_job_match_task` includes `user_id` and `(user_id, task_id)` index for persisted owner isolation.
- Redis is used for hot observability, memory recovery, decision recovery, and RAG query cache; local fallback keeps single-instance demos usable during Redis outages.
- Qdrant can be enabled with `XUNZHI_QDRANT_ENABLED=true`; default remains off so local compilation/runtime is not blocked by external infra.
- Hash embedding fallback uses the configured Qdrant vector size, avoiding fallback/vector-store dimension mismatch.
- PDF resume parsing is still intentionally not faked: upload accepts text-like files and DOCX; PDF/OCR extraction must be wired before claiming production PDF resume ingestion.

## Required Bootstrap

Run these before production use:

1. `admin/src/main/resources/sql/career_resume.sql`
2. `admin/src/main/resources/sql/ai_observability.sql`
3. Configure `xunzhi-agent.agent-binding.*` to existing `agent_properties.ai_name` values or seed matching agent rows.
4. Configure DashScope/Qdrant env vars if external rerank/vector store is required: `DASHSCOPE_API_KEY`, `XUNZHI_QDRANT_ENABLED`, `XUNZHI_QDRANT_HOST`, `XUNZHI_QDRANT_PORT`, `XUNZHI_QDRANT_COLLECTION`.

## Verification Notes

Verified on this branch with JDK 21:

```powershell
mvn.cmd -pl admin "-Dtest=CvOptimizationOrchestratorTest,QdrantResumeVectorStoreTest,LangChain4jAgentAdapterTest,LangChain4jEmbeddingAdapterTest,InterviewPlanningServiceTest,AiCvReviewerTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin "-Dtest=AiCvReviewerTest,InterviewPlanningServiceTest,Bm25ScorerTest,DecisionIndexTest,CvOptimizationOrchestratorTest" "-Denforcer.skip=true" test
```

Both commands pass after fixing Surefire default `argLine` and existing interview test constructors for runtime snapshot/rehydrate dependencies.
