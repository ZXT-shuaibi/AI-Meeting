# JobSpark Knowledge Asset Index

This directory captures the JobSpark-Resume knowledge assets after fusion into AI-Meeting. It is not a verbatim copy of the source README. The original project used Spring Security/JWT and a pure LangChain4j-first architecture; AI-Meeting keeps Sa-Token, Spring AI as the primary model runtime, and LangChain4j behind career adapters.

## Source Assets Reviewed

- `JobSpark-Resume/README.md`: system overview, Multi-Agent flow, RAG pipeline, rendering pipeline, observability, async task model, and API map.
- `JobSpark-Resume/skills/jd-alignment/SKILL.md`: JD-to-resume alignment prompt contract.
- `JobSpark-Resume/skills/question-probing/SKILL.md`: interview probing strategy prompt contract.
- `JobSpark-Resume/src/main/java/.../FileStorageService.java`: Alibaba OSS upload/download client pattern and object-name convention.
- `JobSpark-Resume/src/main/java/.../ExecutorConfig.java`: separate resume task executor and agent observability executor.
- `JobSpark-Resume/src/main/java/.../config/listener/*`: AgentListenerFactory, PersistableAgentListener, and Spring event persistence approach.
- `JobSpark-Resume/src/test/java/.../LangChain4jThreadLocalNpeTest.java`: reproduction of direct Agentic invocation + listener/tool ThreadLocal NPE risk.
- `JobSpark-Resume/src/main/java/.../domain/render/*`: Markdown/HTML/PDF/DOCX rendering pipeline.
- `JobSpark-Resume/sql/*.sql`: resume, interview, and observability table design references.

## AI-Meeting Fusion Map

- Multi-Agent CV optimization maps to `CvOptimizationOrchestrator` and `career-external-ai` Agentic CV runtime.
- Plan-Execute-Reflect interview planning maps to `InterviewPlanningService` and `CareerInterviewExecutionBridge`.
- RAG maps to `ResumeRagService`, `QdrantResumeVectorStore`, BM25, RRF, DashScope rerank, and Redis cache.
- Rendering maps to `CvRendererFacade`, `ResumeMarkdownService`, `PdfResumeRenderBackend`, and `DocxResumeRenderBackend`.
- Async task state maps to `career_resume_parse_task`, `ResumeParseTaskStore`, and `ResumeApplicationService#uploadAsync`.
- OSS handoff maps to `ResumeObjectStorage` with a built-in local backend; cloud OSS SDK remains a backend extension.
- Observability maps to `AiTracePublisher`, Spring events, Redis hot traces, and MySQL cold traces.
- ThreadLocal NPE mitigation maps to `LangChain4jAgenticSafetyPolicy` and adapter-level failure unwrapping.

## Remaining Knowledge-To-Code Gaps

- Runtime skill activation: the JobSpark skill markdown is documented and reflected in prompts, but not yet loaded as callable runtime tools.
- JavaTechInterviewerAgent: still not a standalone Agentic question generator in AI-Meeting; AI-Meeting execution remains authoritative.
- Cloud OSS backend: the adapter exists, but no Alibaba OSS SDK implementation is included in the default build.
- High-fidelity rendering: AI-Meeting uses PDFBox/POI backends; openhtmltopdf/docx4j remains optional future parity work.
- Scanned PDF OCR: text-based PDFBox extraction is implemented; image-only PDFs need an OCR service.
