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
- `AliyunOssResumeObjectStorage` as the cloud backend, following JobSpark's Alibaba OSS pattern with environment credentials, V4 signature, bucket auto-create, upload, and stream download.
- `ResumeParseTaskRecoveryService` as the stale-task repair loop for `PROCESSING/ANALYZING/SAVING` tasks.
- MySQL-first parse task persistence with in-memory fallback.

## Current Behavior

`ResumeApplicationService#uploadAsync`:

1. Validates upload size and type.
2. Reads a bounded snapshot.
3. If object storage is enabled, writes the snapshot to `ResumeObjectStorage` using key `career/resume/parse-task/{taskId}/{filename}`.
4. Persists task metadata with `storage_provider`, `storage_key`, and `file_path`.
5. Enqueues background parse work.
6. Worker reads from object storage when no inline snapshot is present; otherwise it uses the snapshot fallback.

`ResumeParseTaskRecoveryService`:

1. Runs on a scheduled delay controlled by `xunzhi-agent.career.async-task.recovery.*`.
2. Finds active parse tasks whose `update_time` is older than the stale threshold.
3. Requeues tasks with a recoverable local snapshot or object-storage key under the original `taskId`, so frontend status polling remains stable.
4. Completes stale `SAVING` tasks with an existing `resumeId` without saving a duplicate resume, only replaying best-effort embedding and final status persistence.
5. Marks tasks without recoverable file payload as `FAILED` instead of leaving them stuck forever.

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

Tune stale parse task recovery:

```yaml
xunzhi-agent:
  career:
    async-task:
      recovery:
        enabled: true
        stale-after-seconds: 1800
        batch-size: 20
        fixed-delay-millis: 30000
```

Environment variables:

```powershell
$env:XUNZHI_CAREER_ASYNC_TASK_RECOVERY_ENABLED="true"
$env:XUNZHI_CAREER_ASYNC_TASK_RECOVERY_STALE_AFTER_SECONDS="1800"
$env:XUNZHI_CAREER_ASYNC_TASK_RECOVERY_BATCH_SIZE="20"
$env:XUNZHI_CAREER_ASYNC_TASK_RECOVERY_FIXED_DELAY_MILLIS="30000"
```

Enable Alibaba Cloud OSS:

```yaml
xunzhi-agent:
  career:
    storage:
      object-storage:
        enabled: true
        provider: aliyun-oss
        endpoint: oss-cn-beijing.aliyuncs.com
        region: cn-beijing
        bucket-name: xunzhi-resume
        public-base-url: https://cdn.example.com/resume
```

Environment variables:

```powershell
$env:XUNZHI_CAREER_OBJECT_STORAGE_ENABLED="true"
$env:XUNZHI_CAREER_OBJECT_STORAGE_PROVIDER="aliyun-oss"
$env:XUNZHI_CAREER_OBJECT_STORAGE_ENDPOINT="oss-cn-beijing.aliyuncs.com"
$env:XUNZHI_CAREER_OBJECT_STORAGE_REGION="cn-beijing"
$env:XUNZHI_CAREER_OBJECT_STORAGE_BUCKET_NAME="xunzhi-resume"
$env:XUNZHI_CAREER_OBJECT_STORAGE_PUBLIC_BASE_URL="https://cdn.example.com/resume"
$env:OSS_ACCESS_KEY_ID="..."
$env:OSS_ACCESS_KEY_SECRET="..."
```

The default build intentionally does not force the Alibaba OSS SDK into the Spring AI path. The backend loads `com.aliyun.oss:aliyun-sdk-oss` reflectively only when `provider=aliyun-oss` performs real IO, so deployments that enable this provider must place the SDK on the runtime classpath.

## Cloud OSS Backend Requirements

Any additional cloud backend should implement `ResumeObjectStorage` and preserve:

- Stable provider/key/path fields.
- Stream-based download.
- Explicit bucket/container configuration.
- Credentials from environment or managed identity, not hard-coded config.
- Client shutdown if the SDK requires it.
- Object-name sanitization and path traversal protection.
- Upload failure fallback to local snapshot when feasible.

## Known Limits

- Local backend is not shared storage across nodes.
- Alibaba OSS backend gives shared file handoff, but does not by itself provide distributed worker ownership.
- Stale-task recovery is safe for single active scheduler or mostly-idempotent demo deployments, but it does not yet use a distributed lock/lease to prevent two nodes from recovering the same stale task concurrently.
- There is no outbox guarantee for every degraded write.
