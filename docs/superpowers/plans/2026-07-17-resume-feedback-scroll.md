# Resume Feedback Scroll Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show all resume-review sections in a vertically scrollable feedback panel, including a visible empty state when the model omits a section.

**Architecture:** Preserve the existing `CvReview` response contract. The React result component owns presentation: it renders four fixed sections in review order and constrains only the feedback column to a scrollable viewport.

**Tech Stack:** React, TypeScript, Tailwind CSS, Vitest, Testing Library.

---

### Task 1: Cover fixed review section rendering

**Files:**
- Modify: `D:\\IDEA\\Integration\\AI-Meeting-Frontend\\src\\pages\\resume-optimizer\\components\\ResumeOptimizationResult.test.tsx`
- Modify: `D:\\IDEA\\Integration\\AI-Meeting-Frontend\\src\\pages\\resume-optimizer\\components\\ResumeOptimizationResult.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
it("keeps all four review sections visible when optional lists are empty", () => {
  render(<ResumeOptimizationResult {...propsWithEmptyWeaknessesAndSuggestions} />);

  expect(screen.getByRole("heading", { name: "总评" })).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "优势" })).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "不足" })).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "建议" })).toBeInTheDocument();
  expect(screen.getAllByText("暂无内容")).toHaveLength(2);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm.cmd test -- ResumeOptimizationResult.test.tsx`
Expected: FAIL because empty `不足` and `建议` sections are currently hidden.

- [ ] **Step 3: Write minimal implementation**

```tsx
<div className="max-h-[calc(100vh-22rem)] min-h-72 overflow-y-auto pr-3">
  <FeedbackSection title="总评" summary={summary} />
  <FeedbackSection title="优势" items={result.bestReview.strengths} />
  <FeedbackSection title="不足" items={result.bestReview.weaknesses} />
  <FeedbackSection title="建议" items={result.bestReview.suggestions} />
</div>
```

Render `暂无内容` when a section contains neither a summary nor list items. Preserve bold, semantic-color section headings and bullet list presentation.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm.cmd test -- ResumeOptimizationResult.test.tsx`
Expected: PASS.

### Task 2: Verify the frontend contract

**Files:**
- Test: `D:\\IDEA\\Integration\\AI-Meeting-Frontend\\src\\pages\\resume-optimizer\\components\\ResumeOptimizationResult.test.tsx`

- [ ] **Step 1: Run the focused component test**

Run: `npm.cmd test -- ResumeOptimizationResult.test.tsx`
Expected: PASS with all four labels and empty-state content covered.

- [ ] **Step 2: Run the project checks**

Run: `npm.cmd run check`
Expected: PASS for lint, typecheck, and tests.
