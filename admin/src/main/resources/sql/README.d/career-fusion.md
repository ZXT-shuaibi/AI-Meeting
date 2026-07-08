# Career Fusion SQL Bootstrap

Run these SQL files before enabling the career fusion APIs in a shared environment:

1. `career_resume.sql`
   - Creates structured resume storage, persisted RAG chunks, fallback vectors, and JD match task state.
2. `ai_observability.sql`
   - Creates unified AI invocation traces, tool execution traces, and session aggregate stats.

The runtime is intentionally resilient: if these tables are missing or MySQL is unavailable, the career services fall back to in-memory stores where possible. That fallback is suitable for local demos only; production/high-availability deployments must apply the SQL so resume data, match tasks, chunks, and traces survive restarts and multiple app instances.
