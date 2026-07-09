# Qdrant And RAG Notes

## JobSpark Reference

JobSpark used Qdrant as the vector database for resume chunks. The README describes a production RAG chain:

`JD -> HyDE -> Multi-Query -> structured chunks -> hierarchical retrieval -> vector + BM25 -> RRF -> context augmentation -> DashScope rerank`

The Qdrant config included:

- collection name
- host and port
- TLS toggle
- payload text key
- vector size, commonly 1024 for DashScope embedding models
- optional API key

## AI-Meeting Fusion

AI-Meeting implements the same retrieval shape in `ResumeRagService`:

- HyDE hypothetical resume generation.
- Multi-query expansion.
- Coarse recall over overview and skills.
- Fine recall over all chunk types.
- Vector recall through `ResumeVectorStore`.
- BM25 recall through `Bm25Scorer`.
- RRF fusion.
- DashScope rerank gateway.
- Redis cache.

Persistence and fallback:

- `career_resume_chunk` stores chunk text, metadata, vector id, and fallback vector JSON.
- Qdrant is disabled by default so local compile and demos do not require external infrastructure.
- `QdrantResumeVectorStore` deletes existing points by `resume_id` before upsert to avoid stale optimized-resume chunks.
- Hash embedding fallback uses configured vector size to avoid dimension mismatch.

## Bootstrap

Local Qdrant:

```bash
docker run -d --name qdrant -p 6333:6333 -p 6334:6334 qdrant/qdrant
```

AI-Meeting env:

```powershell
$env:XUNZHI_QDRANT_ENABLED="true"
$env:XUNZHI_QDRANT_HOST="localhost"
$env:XUNZHI_QDRANT_PORT="6333"
$env:XUNZHI_QDRANT_COLLECTION="xunzhi_resume"
$env:XUNZHI_QDRANT_VECTOR_SIZE="1024"
```

## Degradation Rules

- If embedding or Qdrant fails, ingestion keeps text chunks so BM25 remains useful.
- If vector recall fails for one query, BM25 and other queries continue.
- If rerank fails, fused candidates are returned.
- If Redis cache fails, retrieval continues without cache.

## Remaining Work

- Add operator runbook for collection migration and vector-size changes.
- Add repair task to re-embed chunks whose external vector id is missing.
- Add metrics for vector path hit rate, BM25-only fallback rate, and rerank failure rate.
