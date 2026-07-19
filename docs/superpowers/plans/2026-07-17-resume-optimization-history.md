# Resume Optimization History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist and restore the most recent 20 resume optimization records, including the uploaded PDF reference, job description, and structured review result.

**Architecture:** Reuse `career_resume_parse_task` because it already owns each uploaded PDF's metadata and storage key. Add job-description and serialized optimization-result fields, then expose user-scoped history APIs. The resume optimizer page loads the latest record on entry and lets the user select any of the latest 20 records.

**Tech Stack:** Spring Boot, MyBatis-Plus, MySQL, React, TypeScript, Vitest.

---

### Task 1: Persist optimization snapshots on parse-task records

**Files:**
- Modify: `D:\\IDEA\\Integration\\AI-Meeting\\admin\\src\\main\\java\\com\\hewei\\hzyjy\\xunzhi\\career\\resume\\application\\ResumeParseTaskRecord.java`
- Modify: `D:\\IDEA\\Integration\\AI-Meeting\\admin\\src\\main\\java\\com\\hewei\\hzyjy\\xunzhi\\career\\resume\\dao\\entity\\CareerResumeParseTaskDO.java`
- Modify: `D:\\IDEA\\Integration\\AI-Meeting\\admin\\src\\main\\java\\com\\hewei\\hzyjy\\xunzhi\\career\\resume\\application\\MySqlResumeParseTaskStore.java`
- Modify: `D:\\IDEA\\Integration\\AI-Meeting\\admin\\src\\main\\resources\\sql\\career_resume.sql`
- Test: `D:\\IDEA\\Integration\\AI-Meeting\\admin\\src\\test\\java\\com\\hewei\\hzyjy\\xunzhi\\career\\resume\\application\\ResumeApplicationServiceTest.java`

- [ ] Add `jobDescription`, `optimizationResultJson`, and `optimizedAt` to the parse-task record and entity mapping.
- [ ] Write a failing service test asserting a completed optimization writes all three fields to the upload record associated with the current user and resume.
- [ ] Add `withOptimization(jobDescription, resultJson)` and persist its fields in MySQL.
- [ ] Add the SQL columns without committing local SQL configuration changes.

### Task 2: Expose user-scoped recent-history APIs

**Files:**
- Create: `D:\\IDEA\\Integration\\AI-Meeting\\admin\\src\\main\\java\\com\\hewei\\hzyjy\\xunzhi\\career\\resume\\application\\ResumeOptimizationHistoryResult.java`
- Modify: `D:\\IDEA\\Integration\\AI-Meeting\\admin\\src\\main\\java\\com\\hewei\\hzyjy\\xunzhi\\career\\resume\\application\\ResumeApplicationService.java`
- Modify: `D:\\IDEA\\Integration\\AI-Meeting\\admin\\src\\main\\java\\com\\hewei\\hzyjy\\xunzhi\\career\\api\\ResumeCareerController.java`
- Test: `D:\\IDEA\\Integration\\AI-Meeting\\admin\\src\\test\\java\\com\\hewei\\hzyjy\\xunzhi\\career\\api\\ResumeCareerControllerTest.java`

- [ ] Write failing tests for a GET endpoint that returns at most 20 records owned by the current user.
- [ ] Map stored result JSON into the existing `CvOptimizationResult` response and return the original filename, task id, resume id, JD, and optimization time.
- [ ] Add `GET /api/xunzhi/v1/resumes/optimization-history`.

### Task 3: Restore a record in the resume optimizer page

**Files:**
- Modify: `D:\\IDEA\\Integration\\AI-Meeting-Frontend\\src\\services\\resumeOptimizerService.ts`
- Modify: `D:\\IDEA\\Integration\\AI-Meeting-Frontend\\src\\services\\resumeOptimizerService.test.ts`
- Modify: `D:\\IDEA\\Integration\\AI-Meeting-Frontend\\src\\pages\\resume-optimizer\\ResumeOptimizerPage.tsx`
- Modify: `D:\\IDEA\\Integration\\AI-Meeting-Frontend\\src\\pages\\resume-optimizer\\ResumeOptimizerPage.test.tsx`

- [ ] Write failing tests for history normalization and automatic restoration of the most recent record on page mount.
- [ ] Add a compact selector for the latest 20 records and restore its JD, resume id, and structured result when selected.
- [ ] Verify focused tests, backend compile/tests, and frontend full checks.
