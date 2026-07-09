# AI-Meeting x JobSpark-Resume Migration Audit

## Runtime Boundary

- AI-Meeting remains the main project and keeps Spring AI as the primary model-call runtime.
- Career fusion code is isolated under `com.hewei.hzyjy.xunzhi.career`.
- LangChain4j is behind `AgentRuntimeGateway` / `EmbeddingGateway`; default build keeps external LangChain4j dependencies in the optional `career-external-ai` profile.
- Default runtime has local Agent Facade beans named `CvReviewer`, `ScoredCvTailor`, `JDAlignmentAgent`, `InterviewCoordinatorAgent`, `InterviewOrchestratorService`, and `InterviewReflectorAgent`, so `LangChain4jAgentAdapter` has a real invocation path even without external LangChain4j jars.
- When the `career-external-ai` profile is enabled, `LangChain4jAgentAdapter` prefers `Agentic*` beans over local facade beans, so real LangChain4j Agentic implementations can take over planning/optimization without replacing AI-Meeting's execution pipeline.
- LangChain4j Agentic direct calls have an explicit safety boundary: `LangChain4jAgentAdapter` unwraps reflective invocation failures before publishing failed traces, and `LangChain4jAgenticSafetyPolicy` fail-fast disables native Agentic listener wiring by default because JobSpark reproduced a managed ThreadLocal NPE risk in direct Agent/listener/tool callbacks.
- Sa-Token remains the auth system. JobSpark JWT/Spring Security is not migrated.

## Migrated Highlights

### P0 Resume + RAG

- Structured resume aggregate `CvBO` and nested resume BOs are available.
- Resume upload stores structured data through `ResumeStore` and triggers best-effort embedding immediately after save.
- Async resume upload is available through `POST /api/xunzhi/v1/resumes/upload-async`, returning a parse `taskId` immediately while a background career executor runs parsing, saving, and best-effort embedding.
- Resume parse tasks persist `PROCESSING/ANALYZING/SAVING/COMPLETED/FAILED/CANCELED` state, progress, owner scope, file metadata, retry lineage, and object-storage handoff metadata in `career_resume_parse_task`.
- Async parse file handoff now goes through `ResumeObjectStorage`: when `xunzhi-agent.career.storage.object-storage.enabled=true`, uploads are written to the configured object-storage adapter and workers read by `storage_key`; if the adapter is disabled or write fails, the bounded local snapshot fallback keeps single-node retry behavior available.
- Object-storage backends now include `LocalResumeObjectStorage` and `AliyunOssResumeObjectStorage`. The Alibaba backend follows JobSpark's OSS pattern while loading the SDK reflectively only when `provider=aliyun-oss` performs real IO, so the default Spring AI path is not coupled to the cloud SDK.
- Stale async parse task recovery is implemented through `ResumeParseTaskRecoveryService`: it scans old `PROCESSING/ANALYZING/SAVING` tasks, requeues recoverable tasks under the original `taskId`, fails tasks with missing payload, and completes `SAVING + resumeId` tasks without saving duplicate resumes.
- Parse task status/list/cancel/retry APIs are owner-scoped: `GET /resumes/parse-tasks/{taskId}`, `GET /resumes/parse-tasks?status=...`, `POST /cancel`, and `POST /retry`.
- Text-based PDF resume parsing is wired through PDFBox 3.x with `RandomAccessReadBuffer` + `Loader.loadPDF(buffer)`, bounded by page and extracted-text limits.
- JobSpark-style multi-format resume delivery is wired from `CvBO -> FreeMarker Markdown template -> CommonMark HTML -> PDF/DOCX` and exposed through `GET /api/xunzhi/v1/resumes/{resumeId}/render/{format}` with owner-scoped lookup.
- Rendered resume artifacts can also be handed off to the configured object-storage adapter through `POST /api/xunzhi/v1/resumes/{resumeId}/render/{format}/store`, returning provider/key/path metadata for downstream download or audit workflows.
- Resume rendering now has an explicit `CvRendererFacade` plus `ResumeTemplateService`, `ResumeMarkdownService`, `PdfResumeRenderBackend`, and `DocxResumeRenderBackend`, matching JobSpark's facade/service split instead of keeping rendering as scattered helper methods.
- Resume rendering supports `markdown/html/pdf/docx` downloads. Markdown/HTML/DOCX preserve full structured resume content; HTML embeds the shared `career-resume/style/cv.css` with `.resume-body`, `.container`, and `@page`; PDF generation uses PDFBox with system CJK-font preference, TTC/TTF probing, and character-level glyph fallback so missing fonts do not break delivery.
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
- JobSpark's structured-output defense is fused at both runtime layers: the default Spring AI/local facade path uses `CareerJsonResponseCleaner` + `AgentResponseParser`, and the `career-external-ai` LangChain4j Agentic interfaces declare `@OutputGuardrails(CareerJsonOutputGuardrail.class)` for CV review/tailor, JD alignment, interview coordination, orchestration, and reflection outputs.
- The orchestrator deep-copies CV state between review/tailor rounds, preventing failed or low-score optimization from mutating the stored primary resume object in memory.
- `ResumeApplicationService` only overwrites the primary stored resume and re-embeds when the score gate passes; failed or low-score optimization results are returned as draft output without corrupting the saved resume.
- `AiCvReviewer` and `AiScoredCvTailor` try `AgentRuntimeGateway` first, then fall back to Spring AI/heuristics.
- `optimize/stream` returns the SSE emitter immediately, executes optimization on the dedicated career task executor, and emits `ITERATION` / `COMPLETE` / `ERROR` events from the background task.

### P2 Plan-Execute-Reflect Interview Planning

- `InterviewPlanningService` adds JD alignment, stage coordination, first-question generation, and reflection routing.
- `career-external-ai` registers real LangChain4j Agentic beans for `AgenticJDAlignmentAgent`, `AgenticInterviewCoordinatorAgent`, `AgenticInterviewReflectorAgent`, and `AgenticInterviewOrchestratorService`.
- JobSpark's `JavaTechInterviewerAgent` is fused as a planning-only question contributor: default runtime exposes a local `JavaTechInterviewerAgent` facade, and `career-external-ai` registers `AgenticJavaTechInterviewerAgent`; both feed first-question suggestions into `InterviewPlanningService` while AI-Meeting still owns question cache, answer submission, scoring, locks, idempotency, snapshots, and report generation.
- JobSpark's `jd-alignment` and `question-probing` Markdown skills are now classpath runtime assets under `career-skills/*`; `CareerSkillRegistry` loads only curated built-in skills and injects them into JD alignment / reflection / Java technical question prompts without loading arbitrary user Markdown.
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
- Legacy AI observability now covers Spring AI compatible chat through `UniversalAiChatHandler`, XingChen workflow streaming chat through `AgentMessageServiceImpl`, and XingChen file upload through `AgentFileAssetServiceImpl`.

### Knowledge Assets

- JobSpark source knowledge has been split into dedicated AI-Meeting fusion docs instead of being copied as a single README dump: `jobspark-knowledge-index.md`, `qdrant-rag-notes.md`, `async-storage-and-threading.md`, `agentic-threadlocal-and-observability.md`, `runtime-skill-assets.md`, and `rendering-and-pdf-notes.md`.
- The docs explicitly separate migrated capabilities from future parity items such as high-fidelity openhtmltopdf/docx4j rendering, strict outbox replay, distributed task recovery leases, frame-level media trace detail, and OCR.

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

1. Multi-format resume rendering is template-pipeline fused and has a real product loop. AI-Meeting now has `CvRendererFacade`, FreeMarker Markdown templates, CommonMark HTML, shared CSS, owner-scoped Markdown/HTML/PDF/DOCX download, and optional object-storage handoff for generated artifacts. It is still not at full JobSpark high-fidelity backend parity because it intentionally keeps PDFBox/POI delivery backends instead of migrating openhtmltopdf CSS/PDF rendering, docx4j XHTML import, advanced template validation, and configurable font/template profiles.
2. JobSpark's async resume parsing task model is fused with an object-storage adapter boundary. AI-Meeting now exposes persistent parse task status/list/cancel/retry, progress, owner isolation, object-storage key/path handoff, local snapshot fallback, Alibaba Cloud OSS backend, and stale active task recovery. It still does not include distributed task ownership/lease or strict outbox replay.
3. JobSpark's AOP/LangChain4j structured-output defense is fused through framework-neutral JSON cleaning plus LangChain4j `@OutputGuardrails` on external Agentic interfaces. AI-Meeting intentionally does not patch third-party LangChain4j source; it keeps the defense at adapter/interface boundaries.
4. LangChain4j Agentic ThreadLocal NPE framework patch is not migrated into third-party source. AI-Meeting instead implements an adapter-level safe-call/listener-disable policy: native Agentic listeners default to `false`, attempts to enable them fail fast, and unified observability stays on `AiTracePublisher`.
5. JobSpark Skill runtime is fused as a curated runtime prompt bridge. `CareerSkillRegistry` loads `jd-alignment` and `question-probing` from classpath resources and injects them into Spring AI/local facade and LangChain4j Agentic planning prompts. This is intentionally not a general `activate_skill` mechanism for arbitrary user Markdown.
6. `JavaTechInterviewerAgent` is fused as a real planning/question-generation contributor. AI-Meeting still owns actual question cache, answer submission, scoring, follow-up persistence, state machine, idempotency, and Single-flight; JobSpark's technical interviewer contributes first-question planning only.
7. Observability now covers career Agent/tool events plus the main legacy Spring AI chat, XingChen workflow chat, XingChen file upload, Xunfei realtime ASR, and Xunfei long-text TTS create/query paths. Xunfei media tracing is entrypoint-level; per-frame ASR packet trace remains local service logging, not persisted Agent trace data.
8. Production-grade fail-closed persistence is not complete. Resume chunks, traces, memory, and decisions have MySQL/Redis paths, but there is no strict outbox or guaranteed replay for every degraded write.
9. JobSpark's docs/knowledge assets are systematized under `docs/career-fusion/*`: Qdrant/RAG, async storage and threading, Agentic ThreadLocal/observability, runtime skill assets, rendering/PDF notes, and the migration index are now available as dedicated topic files.
10. OCR for scanned PDFs is not migrated. PDFBox 3.x text extraction handles text-based PDF resumes; image-only scans still require an OCR service or fallback path.

## Next Fusion Order

1. Harden persistence with an outbox/retry model for RAG chunk, trace, memory, and async task state writes.
2. Add distributed lock/lease ownership around stale async parse task recovery if multi-node workers run the scheduler concurrently.
3. Upgrade rendering backends only if needed: openhtmltopdf/docx4j, configurable fonts, and stricter template validation.
4. Add cloud OCR or OCR-service handoff for scanned/image-only PDFs if scanned resumes become in-scope.
5. Add optional per-frame ASR packet trace persistence only if media debugging needs searchable packet-level history.

## Remaining Limitations

- External LangChain4j integrations are still isolated behind adapters/profile gates; the default runtime uses local facade beans so the migration is demonstrable without forcing LangChain4j jars or external agent services into the Spring AI path.
- Observability is unified for career Agent events, tool executions, legacy Spring AI chat, XingChen workflow chat, XingChen file upload, and Xunfei media ASR/TTS entrypoints.
- Resume/vector persistence is resilient for demos and restart warmup, but it is not yet a strict fail-closed outbox architecture: MySQL chunk persistence failures are logged and the in-memory lane continues.
- `optimize/stream` is asynchronous at the request/thread level and emits iteration/result/error events from a background task, but it is not yet token-by-token model streaming.
- Arbitrary external skill activation, high-fidelity openhtmltopdf/docx4j backends, distributed task recovery leases, strict outbox replay, per-frame ASR trace persistence, and scanned-PDF OCR remain open follow-up migrations.

## Required Bootstrap

Run these before production use:

1. `admin/src/main/resources/sql/career_resume.sql`
2. `admin/src/main/resources/sql/ai_observability.sql`
3. Configure `xunzhi-agent.agent-binding.*` to existing `agent_properties.ai_name` values or seed matching agent rows.
4. Configure DashScope/Qdrant env vars if external rerank/vector store is required: `DASHSCOPE_API_KEY`, `XUNZHI_QDRANT_ENABLED`, `XUNZHI_QDRANT_HOST`, `XUNZHI_QDRANT_PORT`, `XUNZHI_QDRANT_COLLECTION`.
5. Optional async file handoff:
   - local backend: set `XUNZHI_CAREER_OBJECT_STORAGE_ENABLED=true`, `XUNZHI_CAREER_OBJECT_STORAGE_PROVIDER=local`, and configure `XUNZHI_CAREER_OBJECT_STORAGE_BASE_DIR`.
   - Alibaba OSS backend: set `XUNZHI_CAREER_OBJECT_STORAGE_PROVIDER=aliyun-oss`, `XUNZHI_CAREER_OBJECT_STORAGE_ENDPOINT`, `XUNZHI_CAREER_OBJECT_STORAGE_REGION`, `XUNZHI_CAREER_OBJECT_STORAGE_BUCKET_NAME`, and OSS environment credentials.

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
mvn.cmd -pl admin "-Dtest=ResumeApplicationServiceTest,ResumeCareerControllerTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin "-Dtest=AgentResponseParserTest,ResumeRenderServiceTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin "-Dtest=AgentResponseParserTest,ResumeRenderServiceTest,ResumePdfTextExtractorTest,ResumeApplicationServiceTest,ResumeCareerControllerTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin "-Dtest=ResumeApplicationServiceTest,LocalResumeObjectStorageTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin "-Dtest=AliyunOssResumeObjectStorageTest,CareerConfigurationObjectStorageTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin "-Dtest=InMemoryResumeParseTaskStoreTest,ResumeApplicationServiceTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin "-Dtest=AgentFileAssetServiceImplTest,AgentMessageServiceImplTraceTest,UniversalAiChatHandlerTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin -Pcareer-external-ai "-Dtest=CareerJsonOutputGuardrailAnnotationIT,AgentResponseParserTest,AgenticCvOptimizationRuntimeIT,AgenticInterviewPlanningSpringWiringIT" "-Denforcer.skip=true" test
mvn.cmd -pl admin -Pcareer-external-ai "-Dtest=CareerJsonOutputGuardrailAnnotationIT,AgenticCvOptimizationRuntimeIT,AgenticInterviewPlanningSpringWiringIT,LangChain4jAgentAdapterTest,LangChain4jAgenticSafetyPolicyTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin -Pcareer-external-ai -DskipTests "-Denforcer.skip=true" compile
mvn.cmd -pl admin -DskipTests "-Denforcer.skip=true" compile
```
