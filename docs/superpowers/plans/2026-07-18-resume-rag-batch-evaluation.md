# Resume RAG Batch Evaluation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run the 20 local resume-PDF/JD pairs against the local Career API and write per-case and aggregate evaluation results without persisting credentials.

**Architecture:** A PowerShell evaluator logs in once to `http://localhost:8002`, uploads each PDF from `testdata/resume-rag/case-*`, loads the case's single JD text file, invokes resume optimization, and writes CSV/JSON results. A Pester test validates deterministic case discovery and result flattening without calling the API.

**Tech Stack:** PowerShell 7/Windows PowerShell, built-in `Invoke-RestMethod`, Pester when available, Spring Boot Career API.

---

### Task 1: Add test-first evaluation helpers

**Files:**
- Create: `D:\IDEA\Integration\AI-Meeting\scripts\ResumeRagEvaluation.psm1`
- Create: `D:\IDEA\Integration\AI-Meeting\scripts\ResumeRagEvaluation.Tests.ps1`

- [ ] **Step 1: Write the failing test**

```powershell
Import-Module "$PSScriptRoot/ResumeRagEvaluation.psm1" -Force

Describe 'Get-ResumeRagCases' {
    It 'discovers exactly one PDF and one JD text file per case' {
        $root = Join-Path $TestDrive 'resume-rag'
        New-Item -ItemType Directory -Path (Join-Path $root 'case-001') | Out-Null
        Set-Content -LiteralPath (Join-Path $root 'case-001' 'resume.pdf') -Value 'pdf'
        Set-Content -LiteralPath (Join-Path $root 'case-001' 'jd.txt') -Value 'JD'

        $case = Get-ResumeRagCases -Root $root

        $case.Count | Should -Be 1
        $case[0].CaseId | Should -Be 'case-001'
        $case[0].PdfPath | Should -Match 'resume.pdf$'
        $case[0].JobDescription | Should -Be 'JD'
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `Invoke-Pester .\scripts\ResumeRagEvaluation.Tests.ps1 -Output Detailed`

Expected: failure because `ResumeRagEvaluation.psm1` and `Get-ResumeRagCases` do not exist.

- [ ] **Step 3: Write minimal implementation**

```powershell
function Get-ResumeRagCases {
    param([Parameter(Mandatory)][string]$Root)
    Get-ChildItem -LiteralPath $Root -Directory | Sort-Object Name | ForEach-Object {
        $pdf = Get-ChildItem -LiteralPath $_.FullName -File -Filter '*.pdf' | Select-Object -First 1
        $jd = Get-ChildItem -LiteralPath $_.FullName -File -Filter '*.txt' | Select-Object -First 1
        if ($null -eq $pdf -or $null -eq $jd) { throw "Case $($_.Name) must contain one PDF and one JD .txt file" }
        [pscustomobject]@{ CaseId = $_.Name; PdfPath = $pdf.FullName; JobDescription = (Get-Content -LiteralPath $jd.FullName -Raw).Trim() }
    }
}
Export-ModuleMember -Function Get-ResumeRagCases
```

- [ ] **Step 4: Run test to verify it passes**

Run: `Invoke-Pester .\scripts\ResumeRagEvaluation.Tests.ps1 -Output Detailed`

Expected: `Passed: 1, Failed: 0`.

### Task 2: Add API runner and result writer

**Files:**
- Modify: `D:\IDEA\Integration\AI-Meeting\scripts\ResumeRagEvaluation.psm1`
- Create: `D:\IDEA\Integration\AI-Meeting\scripts\Invoke-ResumeRagEvaluation.ps1`

- [ ] **Step 1: Write the failing test**

```powershell
Describe 'ConvertTo-ResumeRagResultRow' {
    It 'preserves the measurable optimization fields' {
        $row = ConvertTo-ResumeRagResultRow -CaseId 'case-001' -Upload @{ data = @{ resumeId = 41; embeddingStatus = 'COMPLETED'; chunkCount = 6 } } -Optimization @{ data = @{ bestReview = @{ score = 0.85 }; scoreGatePassed = $true; iterations = 2; failureReason = $null } } -ElapsedSeconds 12.5
        $row.resumeId | Should -Be 41
        $row.chunkCount | Should -Be 6
        $row.score | Should -Be 0.85
        $row.scoreGatePassed | Should -BeTrue
        $row.elapsedSeconds | Should -Be 12.5
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `Invoke-Pester .\scripts\ResumeRagEvaluation.Tests.ps1 -Output Detailed`

Expected: failure because `ConvertTo-ResumeRagResultRow` does not exist.

- [ ] **Step 3: Write minimal implementation**

```powershell
function ConvertTo-ResumeRagResultRow {
    param($CaseId, $Upload, $Optimization, [double]$ElapsedSeconds)
    [pscustomobject]@{
        caseId = $CaseId; resumeId = $Upload.data.resumeId; embeddingStatus = $Upload.data.embeddingStatus
        chunkCount = $Upload.data.chunkCount; score = $Optimization.data.bestReview.score
        scoreGatePassed = $Optimization.data.scoreGatePassed; iterations = $Optimization.data.iterations
        failureReason = $Optimization.data.failureReason; elapsedSeconds = $ElapsedSeconds
    }
}
```

The runner must post JSON credentials to `/api/xunzhi/v1/users/login`, retain the returned token only in a local variable, use `Authorization: Bearer <token>` for upload and optimization calls, continue after an individual case failure, and create `testdata/resume-rag/results/results.csv` plus `summary.json`.

- [ ] **Step 4: Run test to verify it passes**

Run: `Invoke-Pester .\scripts\ResumeRagEvaluation.Tests.ps1 -Output Detailed`

Expected: all tests pass.

### Task 3: Pilot and batch verification

**Files:**
- Create at runtime: `D:\IDEA\Integration\AI-Meeting\testdata\resume-rag\results\results.csv`
- Create at runtime: `D:\IDEA\Integration\AI-Meeting\testdata\resume-rag\results\summary.json`

- [ ] **Step 1: Run the first case only**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\Invoke-ResumeRagEvaluation.ps1 -BaseUrl http://localhost:8002 -Root .\testdata\resume-rag -Username <runtime-only> -Password <runtime-only> -CaseId case-001`

Expected: one CSV result row with successful upload, `chunkCount > 0`, and a non-empty optimization result or a captured failure reason.

- [ ] **Step 2: Run all 20 cases**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\Invoke-ResumeRagEvaluation.ps1 -BaseUrl http://localhost:8002 -Root .\testdata\resume-rag -Username <runtime-only> -Password <runtime-only>`

Expected: 20 rows in `results.csv`, one per case, and `summary.json` with parse success rate, score-gate pass rate, average score, average iterations, and average elapsed seconds.

- [ ] **Step 3: Inspect output consistency**

Run: `Import-Csv .\testdata\resume-rag\results\results.csv | Group-Object caseId | Where-Object Count -ne 1`

Expected: no output, proving every case has exactly one result row.
