# AI-Meeting x JobSpark-Resume Migration Audit

## Runtime Boundary

- AI-Meeting remains the main project and keeps Spring AI as the primary model-call runtime.
- Career fusion code is isolated under `com.hewei.hzyjy.xunzhi.career`.
- LangChain4j is behind `AgentRuntimeGateway` / `EmbeddingGateway`; default build keeps external LangChain4j dependencies in the optional `career-external-ai` profile.
- Default runtime has local Agent Facade beans named `CvReviewer`, `ScoredCvTailor`, `JDAlignmentAgent`, `InterviewCoordinatorAgent`, `InterviewOrchestratorService`, and `InterviewReflectorAgent`, so `LangChain4jAgentAdapter` has a real invocation path even without external LangChain4j jars.
- When the `career-external-ai` profile is enabled, `LangChain4jAgentAdapter` prefers `Agentic*` beans over local facade beans, so real LangChain4j Agentic implementations can take over planning/optimization without replacing AI-Meeting's execution pipeline.
- Sa-Token remains the auth system. JobSpark JWT/Spring Security is not migrated.

## Migrated Highlights

### P0 Resume + RAG

- Structured resume aggregate `CvBO` and nested resume BOs are available.
- Resume upload stores structured data through `ResumeStore` and triggers best-effort embedding immediately after save.
- Text-based PDF resume parsing is wired through PDFBox 3.x with `RandomAccessReadBuffer` + `Loader.loadPDF(buffer)`, bounded by page and extracted-text limits.
- First-stage multi-format resume delivery is wired from `CvBO -> Markdown -> HTML -> PDF/DOCX` and exposed through `GET /api/xunzhi/v1/resumes/{resumeId}/render/{format}` with owner-scoped lookup.
- Resume rendering supports `markdown/html/pdf/docx` downloads. Markdown/HTML/DOCX preserve full structured resume content; PDF generation uses PDFBox with system CJK-font preference and character-level glyph fallback so missing fonts do not break delivery.
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
- `career-external-ai` registers real LangChain4j Agentic beans for `AgenticJDAlignmentAgent`, `AgenticInterviewCoordinatorAgent`, `AgenticInterviewReflectorAgent`, and `AgenticInterviewOrchestratorService`.
- External Agentic stage planning can return a wrapper result and is unwrapped by the main service without leaking external LangChain4j types into the default runtime.
- External reflection results are accepted only when required fields are valid; malformed output falls back to the local Spring AI/heuristic path instead of silently advancing the interview.
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
- LangChain4j Agentic runtime system prompts are filtered at the adapter boundary so one Agent's role prompt is not replayed into another Agent; business `SYSTEM` messages, compressed history, and `DecisionIndex` context remain preserved.

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
- PDF resume parsing is implemented for text-based PDFs through PDFBox 3.x; scanned-image OCR remains outside the current migrated scope.

## JobSpark Markdown Gap List

The following list is derived from JobSpark `README.md`, `skills/jd-alignment`, `skills/question-probing`, and the supplemental pasted JobSpark notes. Items marked "not fully fused" should not be claimed as complete resume highlights yet.

1. Multi-format resume rendering is first-stage fused, but not yet at full JobSpark fidelity. AI-Meeting now has an end-to-end owner-scoped render API for Markdown/HTML/PDF/DOCX, but it does not yet migrate JobSpark's FreeMarker template validation, CommonMark extension stack, openhtmltopdf CSS/PDF renderer, docx4j XHTML import, or configurable font/template profiles.
2. JobSpark's async resume parsing task model is only partially fused. AI-Meeting upload currently parses and embeds inside the request/service flow with bounded files; it does not yet expose JobSpark-style persistent parse task status, cancel/retry, OSS-first file handoff, or resumable async parsing for long-running uploads.
3. JobSpark Skill runtime is not fully fused. The `jd-alignment` and `question-probing` Markdown skills are reflected in prompts/tests, but there is no runtime `activate_skill`/tool-provider bridge that loads these skill files as callable tools for Agentic agents.
4. `JavaTechInterviewerAgent` is not fully fused as a real Agentic question generator. AI-Meeting still owns actual question cache, answer submission, scoring, follow-up persistence, state machine, idempotency, and Single-flight; JobSpark's interview agents are intentionally limited to planning/reflection decisions for now.
5. Observability is not yet universal across every legacy AI path. Career Agent events and tool executions are unified, but full automatic tracing still requires wiring all legacy Spring AI and Xunfei call sites into `AiTracePublisher`.
6. Production-grade fail-closed persistence is not complete. Resume chunks, traces, memory, and decisions have MySQL/Redis paths, but there is no strict outbox or guaranteed replay for every degraded write.
7. OCR for scanned PDFs is not migrated. PDFBox 3.x text extraction handles text-based PDF resumes; image-only scans still require an OCR service or fallback path.

## Next Fusion Order

1. Add persistent async resume parse tasks with task status/cancel/retry and object-storage handoff, because it turns upload/parse into a high-availability backend story.
2. Upgrade first-stage rendering to JobSpark-level high-fidelity templates if needed: FreeMarker/CommonMark/openhtmltopdf/docx4j plus configurable fonts and template validation.
3. Convert `jd-alignment` and `question-probing` Markdown skills into a runtime skill/tool bridge, then connect them to LangChain4j Agentic agents.
4. Add a real `JavaTechInterviewerAgent` as a planning/question-generation contributor only, while keeping AI-Meeting's existing execution pipeline authoritative.
5. Extend `AiTracePublisher` coverage to all legacy Spring AI/Xunfei model calls.
6. Harden persistence with an outbox/retry model for RAG chunk, trace, memory, and async task state writes.

## Remaining Limitations

- External LangChain4j integrations are still isolated behind adapters/profile gates; the default runtime uses local facade beans so the migration is demonstrable without forcing LangChain4j jars or external agent services into the Spring AI path.
- Observability is unified for the new career Agent events and tool executions, but full automatic tracing of every legacy Spring AI/Xunfei call still depends on wiring those call sites into `AiTracePublisher`.
- Resume/vector persistence is resilient for demos and restart warmup, but it is not yet a strict fail-closed outbox architecture: MySQL chunk persistence failures are logged and the in-memory lane continues.
- `optimize/stream` is asynchronous at the request/thread level and emits iteration/result/error events from a background task, but it is not yet token-by-token model streaming.
- High-fidelity template rendering, persistent async parse task cancellation, runtime Markdown Skill activation, and scanned-PDF OCR remain open follow-up migrations.

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
mvn.cmd -pl admin -Pcareer-external-ai "-Dtest=AgenticCvOptimizationRuntimeIT,AgenticCvOptimizationSpringWiringIT,LangChain4jHybridMemoryAdapterIT,AgenticInterviewPlanningSpringWiringIT,InterviewPlanningServiceTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin "-Dtest=ResumePdfTextExtractorTest,ResumeApplicationServiceTest,LangChain4jAgentAdapterTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin "-Dtest=ResumeRenderServiceTest,ResumeApplicationServiceTest,ResumeCareerControllerTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin -DskipTests "-Denforcer.skip=true" compile
```
