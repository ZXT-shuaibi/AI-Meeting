# AI 监控指标

## 实时运行指标

| 指标 | 计算方式 | 数据来源 | 状态 |
| --- | --- | --- | --- |
| AI invocation success rate | successful END traces / END and ERROR traces | `ai_invocation_trace` | Implemented |
| AI invocation P50/P95/P99 | percentile of `duration_ms` | `ai_invocation_trace` | Implemented |
| Scene volume and latency | grouped by `scene_code` | `ai_invocation_trace` | Implemented |
| RAG stage latency | percentile of `execution_time_ms` by stage | `ai_tool_execution` | Implemented |
| RAG fallback rate | failed enabled-stage events / enabled-stage events | `ai_tool_execution` | Implemented |
| RAG cache hit rate | cache hit events / HyDE or Multi-query events | RAG stage metadata | Pending query aggregation |
| Empty recall rate | empty matched candidates / valid job-match requests | job-match event metadata | Pending instrumentation |
| Token usage | prompt and completion tokens | provider response usage | Pending provider adapter support |
| Cost | tokens multiplied by configured model pricing | token usage and model pricing | Pending token usage |

## 面试流程指标

| 指标 | 计算方式 | 所需事件 |
| --- | --- | --- |
| Automatic transition rate | AUTO transitions / all valid state transitions | state transition event |
| Human takeover rate | sessions with HUMAN transition / all sessions | state transition event |
| Follow-up trigger rate | follow-up transitions / answered questions | state transition event |
| Session recovery success rate | correctly restored sessions / recovery attempts | recovery event |
| State inconsistency rate | inconsistent or duplicate transitions / recovery attempts | recovery event |
| Single-flight merge rate | follower requests / all equivalent requests | single-flight event |
| Stale-write rejection rate | rejected old fencing writes / old write attempts | fencing event |

## 简历优化与岗位匹配

| 指标 | 计算方式 | 所需事件 |
| --- | --- | --- |
| Resume optimization success rate | completed tasks / all tasks | optimization task event |
| Optimization P50/P95 | percentile of task duration | optimization task event |
| Job-match request success rate | successful requests / all requests | job-match event |
| Top-K returned count | returned candidates by requested K | job-match event |
| Low-match suppression | low-match candidates outside Top-K / labeled low-match candidates | offline evaluation |

## 离线质量评测

| 指标 | 计算方式 | 数据来源 |
| --- | --- | --- |
| Hit@K | queries with at least one high-match candidate in Top-K / all queries | qrels evaluation run |
| Recall@K | retrieved high-match candidates / all high-match candidates | qrels evaluation run |
| Precision@K | high-match candidates in Top-K / K | qrels evaluation run |
| MRR | mean reciprocal first high-match rank | qrels evaluation run |
| NDCG@K | mean normalized discounted cumulative gain | graded qrels evaluation run |
| HyDE and rerank delta | metric with switch on minus metric with switch off | paired evaluation runs |

Offline quality metrics must be written by the fixed labeled evaluation set. They must not be inferred from online traffic alone.
