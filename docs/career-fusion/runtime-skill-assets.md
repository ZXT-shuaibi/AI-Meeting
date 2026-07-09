# Runtime Skill Assets

JobSpark included Markdown skills for JD alignment and question probing. AI-Meeting currently uses their ideas in prompts and tests, but does not yet load the skill files as runtime tools.

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
- `InterviewPlanningService#alignWithAgent`
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

Future implementation should add:

- `CareerSkillRegistry`: loads curated skill markdown assets from `docs/career-fusion/skills` or classpath resources.
- `CareerSkillTool`: exposes selected skill content as a LangChain4j tool or prompt-injection-safe context block.
- `SkillInvocationTrace`: publishes tool execution events through `AiTracePublisher`.
- Fail-closed behavior: if skill loading fails, agents continue with embedded prompts and mark metadata `skillDegraded=true`.

Guardrails:

- Do not migrate JobSpark Spring Security/JWT assumptions.
- Do not let skill content override system-level no-fabrication and Sa-Token boundaries.
- Do not load arbitrary user-provided markdown as a trusted skill.
