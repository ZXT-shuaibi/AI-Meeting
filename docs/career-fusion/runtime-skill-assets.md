# Runtime Skill Assets

JobSpark included Markdown skills for JD alignment and question probing. AI-Meeting now loads curated UTF-8 copies of those skills from classpath resources and injects them into planning prompts at runtime.

Implementation:

- Runtime assets live under `admin/src/main/resources/career-skills/jd-alignment` and `admin/src/main/resources/career-skills/question-probing`.
- `CareerSkillRegistry` is the business-facing boundary.
- `ClasspathCareerSkillRegistry` loads only the built-in whitelist and returns an empty prompt section when a skill is missing.
- `InterviewPlanningService` injects `jd-alignment` into JD alignment prompts and `question-probing` into reflection / Java technical question prompts.
- `AgenticJavaTechInterviewerAgent` receives the same curated probing skill context through `AgentRuntimeGateway`.

## JD Alignment Skill Contract

Inputs:

- `jobDescription`: the target JD text.
- `resumeContext`: structured resume context or a compacted resume summary.

Expected analysis:

- Extract hard requirements: education, years of experience, required technologies, and system-domain requirements.
- Extract soft requirements: communication, collaboration, learning ability, ownership.
- Extract priority signals: large-scale system experience, cloud-native exposure, open-source or AI/Agent experience.
- Compare resume skills, project experience, and work history against JD requirements.
- Produce focus areas for interview planning.

Expected output shape:

```json
{
  "matchScore": 85,
  "matchedSkills": ["Java", "Spring Boot", "MySQL"],
  "missingSkills": ["Redis Cluster", "Kubernetes"],
  "relatedProjects": ["high-concurrency system refactor"],
  "focusAreas": ["Redis cluster experience", "cloud-native fundamentals"],
  "suggestion": "Candidate is strong on backend fundamentals; probe distributed-cache and cloud-native gaps."
}
```

AI-Meeting mapping:

- `JdAlignmentResult`
- `InterviewPlanningService#align`
- `AgenticJdAlignmentAgent#align`
- `BusinessAgentScene.JD_ALIGNMENT`

## Question Probing Skill Contract

Inputs:

- `question`: current interview question.
- `answer`: candidate answer.
- `resumeContext`: structured resume context or compacted memory.

Probing strategies:

- Use 5W1H when the answer is vague or incomplete.
- Move from "what" to "why" and "how" when depth is insufficient.
- Ask for metrics, scope, team role, failure mode, and tradeoffs when claims are broad.
- Increase difficulty when the answer is strong.
- Generate one core follow-up at a time.

AI-Meeting mapping:

- `InterviewPlanningService#reflect`
- `ReflectionDecision.PROBE`
- `CareerInterviewExecutionBridge` remains the execution bridge, not the source of truth.

## Proposed Runtime Bridge

Implemented bridge:

- `CareerSkillRegistry`: loads curated skill markdown assets from `docs/career-fusion/skills` or classpath resources.
- Prompt-injection-safe context block: selected skill content is appended to system prompts, not executed as arbitrary code or arbitrary tools.
- Fail-closed behavior: if skill loading fails, agents continue with embedded prompts and mark metadata `skillDegraded=true`.

Still intentionally not implemented:

- A general `activate_skill` mechanism.
- Arbitrary user-provided Markdown skills.
- LangChain4j tool-provider execution for skill files.
- Dedicated `SkillInvocationTrace`; skill usage is currently visible through the surrounding AI invocation trace.

Guardrails:

- Do not migrate JobSpark Spring Security/JWT assumptions.
- Do not let skill content override system-level no-fabrication and Sa-Token boundaries.
- Do not load arbitrary user-provided markdown as a trusted skill.
