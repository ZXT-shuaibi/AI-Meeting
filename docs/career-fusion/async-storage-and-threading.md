# Async Storage And Threading Notes

## JobSpark Reference

JobSpark separated long-running resume work and observability work:

- Resume tasks used a dedicated `resume-task-` executor with CallerRunsPolicy to create backpressure and avoid silently dropping resume processing.
- Observability used a lighter `agent-obs-` executor with DiscardOldestPolicy, treating trace writes as non-critical compared with main business work.
- Resume parse state used `resume_task` with task id, user id, file metadata, file path, status, resume id, error message, start time, complete time, and soft delete.
- File storage used Alibaba OSS with lazy client construction, environment credentials, V4 signature config, bucket creation, upload, download, and delete.

## AI-Meeting Fusion

AI-Meeting uses:

- `careerTaskExecutor` for long-running career tasks, including async parse and SSE optimization.
- `career_resume_parse_task` with owner-scoped task id, progress states, retry lineage, storage provider/key/path, and bounded snapshot fallback.
- `ResumeObjectStorage` as the storage boundary.
- `LocalResumeObjectStorage` as the built-in backend for OSS-style key/path handoff without cloud SDK dependency.
- MySQL-first parse task persistence with in-memory fallback.

## Current Behavior

`ResumeApplicationService#uploadAsync`:

1. Validates upload size and type.
2. Reads a bounded snapshot.
3. If object storage is enabled, writes the snapshot to `ResumeObjectStorage` using key `career/resume/parse-task/{taskId}/{filename}`.
4. Persists task metadata with `storage_provider`, `storage_key`, and `file_path`.
5. Enqueues background parse work.
6. Worker reads from object storage when no inline snapshot is present; otherwise it uses the snapshot fallback.

## Operational Settings

Enable built-in local object storage:

```yaml
xunzhi-agent:
  career:
    storage:
      object-storage:
        enabled: true
        provider: local
        base-dir: ${xunzhi-agent.storage.base-dir}/career/object-storage
```

Environment variables:

```powershell
$env:XUNZHI_CAREER_OBJECT_STORAGE_ENABLED="true"
$env:XUNZHI_CAREER_OBJECT_STORAGE_PROVIDER="local"
$env:XUNZHI_CAREER_OBJECT_STORAGE_BASE_DIR="D:\data\xunzhi-career-object-storage"
```

## Cloud OSS Backend Requirements

A cloud backend should implement `ResumeObjectStorage` and preserve:

- Stable provider/key/path fields.
- Stream-based download.
- Explicit bucket/container configuration.
- Credentials from environment or managed identity, not hard-coded config.
- Client shutdown if the SDK requires it.
- Object-name sanitization and path traversal protection.
- Upload failure fallback to local snapshot when feasible.

## Known Limits

- Local backend is not shared storage across nodes.
- There is no queue-worker recovery scanner yet; if the JVM dies after task creation, a future worker repair task should requeue `PROCESSING/ANALYZING/SAVING` tasks that exceed a timeout.
- There is no outbox guarantee for every degraded write.
