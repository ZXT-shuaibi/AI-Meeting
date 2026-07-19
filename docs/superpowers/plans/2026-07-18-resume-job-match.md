# Resume Job Match Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Let a user manually select completed resume versions, match only those versions against a JD, inspect structured ranked results/history, and open the selected version in resume optimization.

**Architecture:** Extend the job-match API with explicit resumeIds and structured results persisted in the existing matched_templates_json column, preserving old rows without a SQL migration. Add per-resume RAG ranking. Add a protected React page that loads completed parse tasks, sends only selected IDs, renders ranked results/history, and navigates to the optimizer with query parameters.

**Tech Stack:** Spring Boot, Java 21, MyBatis-Plus, Fastjson2, existing RAG/vector store; React, TypeScript, React Router, Tailwind, Vitest.

---

### Task 1: Validate selected resume scope

Files:
- Modify: admin/src/main/java/com/hewei/hzyjy/xunzhi/career/api/io/JobMatchReqDTO.java
- Modify: admin/src/main/java/com/hewei/hzyjy/xunzhi/career/api/ResumeCareerController.java
- Modify: admin/src/main/java/com/hewei/hzyjy/xunzhi/career/resume/application/ResumeApplicationService.java
- Test: admin/src/test/java/com/hewei/hzyjy/xunzhi/career/resume/application/ResumeApplicationServiceTest.java

- [ ] Write tests that invoke matchResumes with empty IDs, duplicate IDs, a foreign ID and an uncompleted ID. Each must throw IllegalArgumentException and never create a task.
- [ ] Run mvn.cmd -q -pl admin -Dtest=ResumeApplicationServiceTest test. Expected: compilation failure before the list argument exists.
- [ ] Add NotEmpty and max 20 validation for List<Long> resumeIds. Pass it from the controller. Change the service signature to accept requested IDs, reject null/empty/duplicate IDs, and confirm every ID has a COMPLETED parse task belonging to current user before creating STARTED.
- [ ] Re-run the test. Expected: PASS.
- [ ] Commit only this API/service/test slice with message: feat: require selected resumes for job matching.

### Task 2: Persist structured candidates and user history without SQL changes

Files:
- Create: admin/src/main/java/com/hewei/hzyjy/xunzhi/career/resume/application/JobMatchCandidate.java
- Create: admin/src/main/java/com/hewei/hzyjy/xunzhi/career/resume/application/JobMatchHistoryItem.java
- Modify: admin/src/main/java/com/hewei/hzyjy/xunzhi/career/resume/application/JobMatchTaskResult.java
- Modify: admin/src/main/java/com/hewei/hzyjy/xunzhi/career/resume/application/JobMatchTaskStore.java
- Modify: admin/src/main/java/com/hewei/hzyjy/xunzhi/career/resume/application/InMemoryJobMatchTaskStore.java
- Modify: admin/src/main/java/com/hewei/hzyjy/xunzhi/career/resume/application/MySqlJobMatchTaskStore.java
- Modify: admin/src/main/java/com/hewei/hzyjy/xunzhi/career/resume/dao/mapper/CareerJobMatchTaskMapper.java
- Test: admin/src/test/java/com/hewei/hzyjy/xunzhi/career/resume/application/MySqlJobMatchTaskStoreTest.java

- [ ] Write a persistence test for candidate resumeId 11, filename ai-resume.pdf, rank 1, score 86, matched RAG/Spring AI and missing Kubernetes, with selected IDs 11/12. Reload it by user and assert all values survive.
- [ ] Run mvn.cmd -q -pl admin -Dtest=MySqlJobMatchTaskStoreTest test. Expected: failure because typed result models are absent.
- [ ] Add JobMatchCandidate(resumeId, originalFilename, rank, matchScore, recommendationLevel, matchedPoints, missingPoints) and extend JobMatchTaskResult with selectedResumeIds and matchedResumes. Serialize one object with those fields into existing matched_templates_json. Decode historic JSON arrays as empty structured results. Add findRecentByUserId(userId, 20) in store/mapper and map it to JobMatchHistoryItem.
- [ ] Re-run persistence test. Expected: PASS.
- [ ] Commit only result/store/mapper/test slice with message: feat: persist structured job match results.

### Task 3: Rank every selected resume and explain alignment

Files:
- Create: admin/src/main/java/com/hewei/hzyjy/xunzhi/career/resume/rag/ResumeRagMatch.java
- Modify: admin/src/main/java/com/hewei/hzyjy/xunzhi/career/resume/rag/ResumeRagService.java
- Modify: admin/src/main/java/com/hewei/hzyjy/xunzhi/career/resume/application/ResumeApplicationService.java
- Test: admin/src/test/java/com/hewei/hzyjy/xunzhi/career/resume/rag/ResumeRagServiceTest.java
- Test: admin/src/test/java/com/hewei/hzyjy/xunzhi/career/resume/application/ResumeApplicationServiceTest.java

- [ ] Write RAG test: scope 11/12 for Spring AI RAG returns one ResumeRagMatch per selected ID sorted by score.
- [ ] Run mvn.cmd -q -pl admin -Dtest=ResumeRagServiceTest test. Expected: failure because retrieveResumeMatches does not exist.
- [ ] Add retrieveResumeMatches without changing retrieveTemplates. Reuse fused chunks, group by resumeId, aggregate RRF score/evidence, include selected IDs with zero score when no chunk matches. In application service enrich from completed parse tasks/CV data, tokenize JD against each CV for matched/missing terms, normalize score to 0-100 and label ranks 最推荐/推荐/可备选.
- [ ] Run mvn.cmd -q -pl admin -Dtest=ResumeRagServiceTest,ResumeApplicationServiceTest test. Expected: PASS.
- [ ] Commit only RAG/service/tests with message: feat: rank selected resume versions by job match.

### Task 4: Expose user-scoped history API

Files:
- Modify: admin/src/main/java/com/hewei/hzyjy/xunzhi/career/api/ResumeCareerController.java
- Modify: admin/src/main/java/com/hewei/hzyjy/xunzhi/career/resume/application/ResumeApplicationService.java
- Test: admin/src/test/java/com/hewei/hzyjy/xunzhi/career/api/ResumeCareerControllerTest.java

- [ ] Write controller test for GET /api/xunzhi/v1/jobs/match-history as user 7; it verifies listMatchHistory(7L) and no userId in response.
- [ ] Run mvn.cmd -q -pl admin -Dtest=ResumeCareerControllerTest test. Expected: route missing failure.
- [ ] Implement current-user-only route and service method returning at most 20 entries.
- [ ] Re-run controller test. Expected: PASS.
- [ ] Commit only history endpoint/service/test with message: feat: add user-scoped job match history.

### Task 5: Add frontend service, protected route and sidebar entry

Files:
- Create: src/pages/resume-job-match/ResumeJobMatchPage.tsx
- Create: src/pages/resume-job-match/ResumeJobMatchPage.test.tsx
- Modify: src/services/resumeOptimizerService.ts
- Modify: src/services/resumeOptimizerService.test.ts
- Modify: src/lib/constants.ts
- Modify: src/app/router.tsx
- Modify: src/app/router.test.tsx
- Modify: src/components/layout/sidebar/SidebarNav.tsx
- Modify: src/components/layout/sidebar/SidebarNav.test.tsx

- [ ] Write failing tests asserting route /resume-job-match, heading 岗位匹配, sidebar link, and post body containing jobDescription/resumeIds/limit.
- [ ] Run npm.cmd run test:run -- src/services/resumeOptimizerService.test.ts src/app/router.test.tsx src/components/layout/sidebar/SidebarNav.test.tsx. Expected: failure before route/service members exist.
- [ ] Add typed candidate/task/history normalizers, matchResumes, getMatchHistory, route constant, lazy protected route, and sidebar button immediately after 简历优化.
- [ ] Re-run selected frontend tests. Expected: PASS.
- [ ] Commit only route/service/sidebar/tests with message: feat: add resume job match route.

### Task 6: Build manual selection workflow and optimization hand-off

Files:
- Modify: src/pages/resume-job-match/ResumeJobMatchPage.tsx
- Modify: src/pages/resume-job-match/ResumeJobMatchPage.test.tsx
- Modify: src/pages/resume-optimizer/ResumeOptimizerPage.tsx
- Modify: src/pages/resume-optimizer/ResumeOptimizerPage.test.tsx

- [ ] Write page test: user checks ai-resume.pdf, enters JD, clicks 开始匹配, sees 最推荐 with RAG, then clicks 用这份简历去优化 and navigation includes resumeId=11 and jobDescription.
- [ ] Run npm.cmd run test:run -- src/pages/resume-job-match/ResumeJobMatchPage.test.tsx src/pages/resume-optimizer/ResumeOptimizerPage.test.tsx. Expected: page/query hand-off failure.
- [ ] Load only COMPLETED parse tasks with resumeId; use checkbox state, require at least one selection and JD, preserve inputs on errors, render candidates by rank and server history. Do not cache matching data globally. In optimizer, read resumeId/jobDescription with useSearchParams, display selected-version context, and only start optimization through an explicit button.
- [ ] Re-run page tests. Expected: PASS.
- [ ] Commit only pages/tests with message: feat: add selected resume job match workflow.

### Task 7: Verify and stage safely

Files: verify only. Never stage .env.example, application.yaml, vite.config.ts, admin/src/main/resources/sql, career prompt resources, generated docs, or existing unrelated interview/report changes.

- [ ] Run backend focused tests and mvn.cmd -q -pl admin -DskipTests compile. Expected: PASS and BUILD SUCCESS.
- [ ] Run frontend focused tests and npm.cmd run typecheck. Expected: exit code 0.
- [ ] Review git status in both repositories. Commit/push only job-match source/tests after checking exact staged lists.

