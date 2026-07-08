# AI-Meeting x JobSpark-Resume Migration Audit

## Runtime Boundary

- AI-Meeting remains the main project and keeps Spring AI as the primary model-call runtime.
- Career fusion code is isolated under `com.hewei.hzyjy.xunzhi.career`.
- LangChain4j is behind `AgentRuntimeGateway` / `EmbeddingGateway`; default build keeps external LangChain4j dependencies in the optional `career-external-ai` profile.
- Default runtime has local Agent Facade beans named `CvReviewer`, `ScoredCvTailor`, `JDAlignmentAgent`, `InterviewCoordinatorAgent`, `InterviewOrchestratorService`, and `InterviewReflectorAgent`, so `LangChain4jAgentAdapter` has a real invocation path even without external LangChain4j jars.
- Sa-Token remains the auth system. JobSpark JWT/Spring Security is not migrated.

## Migrated Highlights

### P0 Resume + RAG

- Structured resume aggregate `CvBO` and nested resume BOs are available.
- Resume upload stores structured data through `ResumeStore` and triggers best-effort embedding immediately after save.
- MySQL-first `MySqlResumeStore` is backed by in-memory fallback and enforces owner lookup through `findByIdAndUserId`.
- RAG chunking uses overview / summary / skills / experience / project / education chunks with `user_id` and `resume_id` metadata.
- Retrieval keeps HyDE, multi-query, hierarchical coarse recall, vector + BM25 fine recall, RRF fusion, context augmentation, DashScope rerank gateway, Redis cache, and BM25 fallback.
- BM25 is an independent recall lane over persisted/in-memory resume chunks, not only a re-sort of vector hits; vector lane and BM25 lane are fused through RRF before context augmentation.
- Embedding/vector recall exceptions are isolated per query, so BM25 recall and rerank fallback can still return candidates when the vector path is unavailable.
- Ingestion stores text-only chunks when embedding fails, so BM25 has persisted/in-memory corpus even before vector embedding recovers.
- Resume chunks and fallback vectors are persisted in `career_resume_chunk` so local vector search can warm up after restart.
- Qdrant REST adapter is implemented and disabled by default; when enabled it deletes existing points by `resume_id` before upsert to avoid stale optimized-resume chunks, and short backoff prevents repeated calls while Qdrant is unavailable.

### P1 Judge-Executor CV Optimization

- `CvOptimizationOrchestrator` implements the reviewer-tailor loop.
- Exit rule: `score > 0.8`, max iteration default 3.
- On max iterations, the orchestrator returns the latest reviewed CV, not an unreviewed post-tailor version.
- LLM/parse failure returns the latest usable result.
- The orchestrator deep-copies CV state between review/tailor rounds, preventing failed or low-score optimization from mutating the stored primary resume object in memory.
- `ResumeApplicationService` only overwrites the primary stored resume and re-embeds when the score gate passes; failed or low-score optimization results are returned as draft output without corrupting the saved resume.
- `AiCvReviewer` and `AiScoredCvTailor` try `AgentRuntimeGateway` first, then fall back to Spring AI/heuristics.
- `optimize/stream` returns the SSE emitter immediately, executes optimization on the dedicated career task executor, and emits `ITERATION` / `COMPLETE` / `ERROR` events from the background task.

### P2 Plan-Execute-Reflect Interview Planning

- `InterviewPlanningService` adds JD alignment, stage coordination, first-question generation, and reflection routing.
- `ReflectionResult` returns `PROBE / NEXT / STAGE_FINISH / FINISH` decisions.
- `CareerInterviewExecutionBridge` publishes generated plans into AI-Meeting's existing interview question cache, initializes interview flow, writes suggestions/direction, marks the session `READY`, and refreshes the runtime snapshot.
- The bridge is fail-closed for executable-plan publication: if questions/flow cannot be read back from the original execution cache, the session is not marked `READY`.
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
- Completed/failed cold-trace updates persist `metadata_json`, so degraded/fallback flags from Spring AI and Agent adapters survive beyond Redis hot traces.
- Session aggregate stats are stored in `ai_agent_session_stats`.
- `@EnableAsync` is enabled so event listeners can run asynchronously.
- Career long-running work uses a dedicated `careerTaskExecutor`, isolating resume optimization SSE tasks from controller request threads.

## High-Availability Status

- Resume data, job match tasks, RAG chunks, fallback vectors, traces, and stats have MySQL persistence paths.
- `career_job_match_task` includes `user_id` and `(user_id, task_id)` index for persisted owner isolation.
- Redis is used for hot observability, memory recovery, decision recovery, and RAG query cache; local fallback keeps single-instance demos usable during Redis outages.
- Qdrant can be enabled with `XUNZHI_QDRANT_ENABLED=true`; default remains off so local compilation/runtime is not blocked by external infra.
- Hash embedding fallback uses the configured Qdrant vector size, avoiding fallback/vector-store dimension mismatch.
- Career API DTOs and service layer cap JD/question/answer input lengths, resume upload has a dedicated 6 MB request filter plus 5 MB file limit, DOCX central-directory entry count/entry size/total uncompressed size are bounded, and extracted text is truncated before structure inference.
- PDF resume parsing is intentionally not faked: upload accepts text-like files and DOCX; PDF/OCR extraction must be wired before claiming production PDF resume ingestion.

## Remaining Limitations

- External LangChain4j integrations are still isolated behind adapters/profile gates; the default runtime uses local facade beans so the migration is demonstrable without forcing LangChain4j jars or external agent services into the Spring AI path.
- Observability is unified for the new career Agent events and tool executions, but full automatic tracing of every legacy Spring AI/Xunfei call still depends on wiring those call sites into `AiTracePublisher`.
- Resume/vector persistence is resilient for demos and restart warmup, but it is not yet a strict fail-closed outbox architecture: MySQL chunk persistence failures are logged and the in-memory lane continues.
- `optimize/stream` is asynchronous at the request/thread level and emits iteration/result/error events from a background task, but it is not yet token-by-token model streaming.

## Required Bootstrap

Run these before production use:

1. `admin/src/main/resources/sql/career_resume.sql`
2. `admin/src/main/resources/sql/ai_observability.sql`
3. Configure `xunzhi-agent.agent-binding.*` to existing `agent_properties.ai_name` values or seed matching agent rows.
4. Configure DashScope/Qdrant env vars if external rerank/vector store is required: `DASHSCOPE_API_KEY`, `XUNZHI_QDRANT_ENABLED`, `XUNZHI_QDRANT_HOST`, `XUNZHI_QDRANT_PORT`, `XUNZHI_QDRANT_COLLECTION`.

## Verification Notes

Verified on this branch with JDK 21:

```powershell
mvn.cmd -pl admin "-Dtest=ResumeRagServiceTest,ResumeApplicationServiceTest,CareerResumeUploadSizeFilterTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin "-Dtest=CvOptimizationOrchestratorTest,ResumeRagServiceTest,CareerInterviewExecutionBridgeTest,ResumeApplicationServiceTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin "-Dtest=ResumeCareerControllerTest,AiTraceEventListenerTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin "-Dtest=AiCvReviewerTest,InterviewPlanningServiceTest,Bm25ScorerTest,DecisionIndexTest,CvOptimizationOrchestratorTest,QdrantResumeVectorStoreTest,LangChain4jAgentAdapterTest,LangChain4jEmbeddingAdapterTest,CareerInterviewExecutionBridgeTest,ResumeRagServiceTest,ResumeApplicationServiceTest,CareerResumeUploadSizeFilterTest,ResumeCareerControllerTest,AiTraceEventListenerTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin -DskipTests "-Denforcer.skip=true" compile
```
