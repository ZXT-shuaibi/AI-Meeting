# Career Runtime Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 AI-Meeting 的 career 域运行时边界切分为“简历优化 Agent 使用 LangChain4j、简历基础服务保留 Java/Spring、面试规划与反思锁定 Spring AI”，并继续融合 JobSpark 的简历优化能力。

**Architecture:** 保留现有 Spring/Java 的简历上传、解析、RAG、渲染、对象存储与任务恢复主链，只让 `CvReviewer`、`ScoredCvTailor` 与 `CvOptimizationOrchestrator` 继续沿用 LangChain4j external-ai 能力。Interview 主链从 `AgentRuntimeGateway` 完全收口到 Spring AI，本轮不删除 external-ai interview 文件，只断开调用与注册生效路径。

**Tech Stack:** Spring Boot, Spring AI, LangChain4j Agentic, JUnit 5, Mockito, SSE (`SseEmitter`), Fastjson2, YAML Configuration Properties

---

## 文件结构与职责

### 本轮核心修改文件

- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/interview/InterviewPlanningService.java`
  责任：移除 Interview 对 LangChain4j runtime 的调用依赖，彻底锁定 Spring AI + 本地 fallback。
- Modify: `admin/src/career-external-ai/java/com/hewei/hzyjy/xunzhi/career/config/AgenticInterviewRuntimeConfiguration.java`
  责任：让 interview external-ai wiring 不再作为正式运行路径。
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AiCvReviewer.java`
  责任：接入 JobSpark Reviewer 强提示词资源，统一 LangChain4j 优先调用与启发式兜底。
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AiScoredCvTailor.java`
  责任：接入 JobSpark Tailor 强提示词资源。
- Modify: `admin/src/career-external-ai/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AgenticCvReviewAgent.java`
  责任：让 external-ai 的 Reviewer 与资源化 prompt 对齐。
- Modify: `admin/src/career-external-ai/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AgenticScoredCvTailorAgent.java`
  责任：让 external-ai 的 Tailor 与资源化 prompt 对齐。
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/skill/ClasspathCareerSkillRegistry.java`
  责任：加载 `cv-reviewer` 与 `cv-tailor` 资源。
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/cv/CvOptimizationOrchestrator.java`
  责任：增加逐轮 callback 与配置化 score gate / max iterations。
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/resume/application/ResumeApplicationService.java`
  责任：向 orchestrator 透传 progress callback，保留现有基础服务职责。
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/api/ResumeCareerController.java`
  责任：把 `optimizeStream` 从“事后回放”改成“逐轮推送”。
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/support/CareerJsonResponseCleaner.java`
  责任：补齐 tool-call 检测与 JobSpark 的多策略 JSON 提取。
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/config/CareerConfiguration.java`
  责任：注册优化配置属性。
- Create: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/config/CareerOptimizationProperties.java`
  责任：承载 `max-iterations` 与 `score-gate`。
- Modify: `admin/src/main/resources/application.yaml`
  责任：新增 `xunzhi-agent.career.optimization.*` 配置。
- Create: `admin/src/main/resources/career-skills/cv-reviewer/SKILL.md`
  责任：存放 Reviewer 资源化 prompt。
- Create: `admin/src/main/resources/career-skills/cv-tailor/SKILL.md`
  责任：存放 Tailor 资源化 prompt。

### 本轮测试文件

- Modify: `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/interview/InterviewPlanningServiceTest.java`
- Modify: `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AiCvReviewerTest.java`
- Modify: `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/cv/CvOptimizationOrchestratorTest.java`
- Modify: `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/api/ResumeCareerControllerTest.java`
- Create: `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/support/CareerJsonResponseCleanerTest.java`
- Modify: `admin/src/career-external-ai-test/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AgenticCvOptimizationSpringWiringIT.java`
- Modify: `admin/src/career-external-ai-test/java/com/hewei/hzyjy/xunzhi/career/agent/interview/AgenticInterviewPlanningSpringWiringIT.java`

## Task 1: 锁定 Interview 主链到 Spring AI

**Files:**
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/interview/InterviewPlanningService.java`
- Modify: `admin/src/career-external-ai/java/com/hewei/hzyjy/xunzhi/career/config/AgenticInterviewRuntimeConfiguration.java`
- Modify: `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/interview/InterviewPlanningServiceTest.java`
- Modify: `admin/src/career-external-ai-test/java/com/hewei/hzyjy/xunzhi/career/agent/interview/AgenticInterviewPlanningSpringWiringIT.java`

- [ ] **Step 1: 先写 Interview 主链不再依赖 AgentRuntimeGateway 的失败测试**

```java
@Test
void planAndReflectStayOnSpringAiWithoutAgentRuntimeGateway() {
    AiGateway aiGateway = request -> AiGatewayResult.builder()
            .content("{\"score\":0.8,\"matchedSkills\":[\"java\"],\"missingSkills\":[],\"summary\":\"ok\"}")
            .provider("test")
            .build();
    HybridCompactingChatMemory memory = new HybridCompactingChatMemory(
            aiGateway,
            new CareerMessageImportanceScorer(),
            new DecisionIndex()
    );
    InterviewPlanningService service = new InterviewPlanningService(
            aiGateway,
            memory,
            CareerSkillRegistry.disabled()
    );

    CvBO cv = CvBO.builder().id(1L).name("candidate").summary("java backend").build();
    InterviewPlan plan = service.plan("memory-1", "session-1", cv, "Java backend JD");
    ReflectionResult reflection = service.reflect("memory-1", "介绍一个项目", "我做过缓存优化和接口压测", cv);

    assertNotNull(plan);
    assertNotNull(reflection);
}
```

- [ ] **Step 2: 运行测试，确认当前实现还依赖 LangChain4j 分支**

Run:
```powershell
mvn.cmd -pl admin "-Dtest=InterviewPlanningServiceTest" "-Denforcer.skip=true" test
```

Expected:
- 现有测试需要调整，或新增测试在当前实现下失败。

- [ ] **Step 3: 在 `InterviewPlanningService` 中删除 LangChain4j 调用分支，仅保留 Spring AI 路径**

目标改动要点：

```java
// 删除字段
private final ObjectProvider<AgentRuntimeGateway> agentRuntimeGatewayProvider;

// plan() 中删除
InterviewPlan plan = tryLangChain4jPlan(...);

// reflect() 中删除
ReflectionResult agentResult = tryLangChain4jReflect(...);

// align() / coordinateStages() / generateFirstQuestion() 改为直接走 Spring AI / 本地规则
```

保留：
- `CareerSkillRegistry`
- `HybridCompactingChatMemory`
- 本地 `heuristicDecision(...)`
- Spring AI prompt 组装

- [ ] **Step 4: 调整 external-ai interview wiring，让其不再作为正式运行路径**

推荐最小改法：

```java
@Configuration
@ConditionalOnProperty(
        prefix = "xunzhi-agent.career.interview.agentic",
        name = "enabled",
        havingValue = "true"
)
public class AgenticInterviewRuntimeConfiguration {
    ...
}
```

并在 `application.yaml` 中不提供开启值，让默认运行时永远不走它。

- [ ] **Step 5: 更新 Interview 相关测试**

补充断言方向：
- `InterviewPlanningServiceTest` 不依赖 `AgentRuntimeGateway`
- `AgenticInterviewPlanningSpringWiringIT` 改为验证默认情况下 interview agentic wiring 不启用，或仅在显式属性下启用

示例断言：

```java
assertFalse(applicationContext.containsBean("AgenticJDAlignmentAgent"));
assertFalse(applicationContext.containsBean("AgenticInterviewCoordinatorAgent"));
```

- [ ] **Step 6: 运行 Interview 相关测试**

Run:
```powershell
mvn.cmd -pl admin "-Dtest=InterviewPlanningServiceTest,CareerInterviewExecutionBridgeTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin -Pcareer-external-ai "-Dtest=AgenticInterviewPlanningSpringWiringIT" "-Denforcer.skip=true" test
```

Expected:
- 所有测试 PASS
- 默认路径下 interview 不再依赖 LangChain4j runtime

- [ ] **Step 7: 提交**

```powershell
git add admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/interview/InterviewPlanningService.java admin/src/career-external-ai/java/com/hewei/hzyjy/xunzhi/career/config/AgenticInterviewRuntimeConfiguration.java admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/interview/InterviewPlanningServiceTest.java admin/src/career-external-ai-test/java/com/hewei/hzyjy/xunzhi/career/agent/interview/AgenticInterviewPlanningSpringWiringIT.java admin/src/main/resources/application.yaml
git commit -m "refactor: lock interview planning and reflection to spring ai"
```

## Task 2: 迁移 Reviewer / Tailor Prompt 到资源文件并对齐 LangChain4j

**Files:**
- Create: `admin/src/main/resources/career-skills/cv-reviewer/SKILL.md`
- Create: `admin/src/main/resources/career-skills/cv-tailor/SKILL.md`
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/skill/ClasspathCareerSkillRegistry.java`
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AiCvReviewer.java`
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AiScoredCvTailor.java`
- Modify: `admin/src/career-external-ai/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AgenticCvReviewAgent.java`
- Modify: `admin/src/career-external-ai/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AgenticScoredCvTailorAgent.java`
- Modify: `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AiCvReviewerTest.java`
- Modify: `admin/src/career-external-ai-test/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AgenticCvOptimizationSpringWiringIT.java`

- [ ] **Step 1: 新增 Reviewer 资源文件的失败测试或加载测试**

在 `ClasspathCareerSkillRegistryTest` 或新增测试中添加：

```java
@Test
void loadsCvReviewerAndCvTailorSkills() {
    ClasspathCareerSkillRegistry registry = ClasspathCareerSkillRegistry.withBuiltIns();

    assertTrue(registry.find("cv-reviewer").isPresent());
    assertTrue(registry.find("cv-tailor").isPresent());
}
```

- [ ] **Step 2: 运行技能加载测试，确认当前资源尚不存在**

Run:
```powershell
mvn.cmd -pl admin "-Dtest=ClasspathCareerSkillRegistryTest" "-Denforcer.skip=true" test
```

Expected:
- 测试失败，因为 `cv-reviewer` 与 `cv-tailor` 尚未注册。

- [ ] **Step 3: 创建 `cv-reviewer` 与 `cv-tailor` 资源文件**

`cv-reviewer/SKILL.md` 至少包含：
- 角色定位
- 35/30/25/10 四维评分
- 评分等级表
- `strengths / weaknesses / suggestions`
- 参考模板使用方式

`cv-tailor/SKILL.md` 至少包含：
- 真实性底线 5 条
- 重写 / 强调 / 结构调整策略
- 基于 review 的优势强化、劣势补强、缺口填补
- 输出必须兼容 `CvBO`

- [ ] **Step 4: 扩展 `ClasspathCareerSkillRegistry.withBuiltIns()`**

```java
public static ClasspathCareerSkillRegistry withBuiltIns() {
    Map<String, CareerSkill> loaded = new LinkedHashMap<>();
    loadSkill(loaded, "jd-alignment", List.of("jd-template.md"));
    loadSkill(loaded, "question-probing", List.of("probing-strategies.md"));
    loadSkill(loaded, "cv-reviewer", List.of());
    loadSkill(loaded, "cv-tailor", List.of());
    return new ClasspathCareerSkillRegistry(loaded);
}
```

- [ ] **Step 5: 让 `AiCvReviewer` 与 `AiScoredCvTailor` 读取资源 prompt**

`AiCvReviewer` 目标结构：

```java
private final CareerSkillRegistry skillRegistry;

String systemPrompt = """
Review the resume against the JD and return JSON.
""" + skillRegistry.promptSection("cv-reviewer");
```

`AiScoredCvTailor` 目标结构：

```java
String systemPrompt = """
Rewrite resume wording based only on existing facts and return JSON.
""" + skillRegistry.promptSection("cv-tailor");
```

同时保持：
- Reviewer：LangChain4j 优先，Spring AI fallback
- Tailor：LangChain4j 优先，Spring AI fallback

- [ ] **Step 6: 对齐 external-ai 的 Agent 注解 prompt**

推荐做法：
- 将资源内容提炼为一个共享常量提供器，例如 `CareerPromptLibrary`
- `AgenticCvReviewAgent` / `AgenticScoredCvTailorAgent` 改为引用同源文本

如果注解无法动态引用完整资源，则在计划实现中允许：
- 先把同一份 prompt 文本同步进 external-ai 注解
- 并补测试确保两边至少包含同样的关键字段

- [ ] **Step 7: 调整 Reviewer 测试**

```java
@Test
void usesStructuredLlmScoreBeforeHeuristicFallback() {
    AiCvReviewer reviewer = new AiCvReviewer(
            request -> AiGatewayResult.builder()
                    .content("{\"score\":0.86,\"feedback\":\"Strong JD fit\"}")
                    .provider("test")
                    .build(),
            CareerSkillRegistry.disabled()
    );

    CvReview review = reviewer.review(CvBO.builder().summary("java redis").build(), "python frontend", List.of());

    assertEquals(0.86, review.score());
}
```

- [ ] **Step 8: 运行 CV prompt / wiring 测试**

Run:
```powershell
mvn.cmd -pl admin "-Dtest=AiCvReviewerTest,ClasspathCareerSkillRegistryTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin -Pcareer-external-ai "-Dtest=AgenticCvOptimizationSpringWiringIT" "-Denforcer.skip=true" test
```

Expected:
- 新增 skill 被成功加载
- Reviewer 仍能解析结构化分数
- external-ai CV wiring 正常

- [ ] **Step 9: 提交**

```powershell
git add admin/src/main/resources/career-skills/cv-reviewer/SKILL.md admin/src/main/resources/career-skills/cv-tailor/SKILL.md admin/src/main/java/com/hewei/hzyjy/xunzhi/career/skill/ClasspathCareerSkillRegistry.java admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AiCvReviewer.java admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AiScoredCvTailor.java admin/src/career-external-ai/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AgenticCvReviewAgent.java admin/src/career-external-ai/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AgenticScoredCvTailorAgent.java admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AiCvReviewerTest.java admin/src/career-external-ai-test/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AgenticCvOptimizationSpringWiringIT.java
git commit -m "feat: migrate jobspark cv reviewer and tailor prompts"
```

## Task 3: 恢复逐轮 SSE 进度推送

**Files:**
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/cv/CvOptimizationOrchestrator.java`
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/resume/application/ResumeApplicationService.java`
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/api/ResumeCareerController.java`
- Modify: `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/cv/CvOptimizationOrchestratorTest.java`
- Modify: `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/api/ResumeCareerControllerTest.java`

- [ ] **Step 1: 先写 orchestrator callback 的失败测试**

```java
@Test
void invokesProgressCallbackForEachReviewRound() {
    AtomicInteger callbackCount = new AtomicInteger();
    CvReviewer reviewer = (cv, jd, templates) -> new CvReview(
            callbackCount.get() == 0 ? 0.70 : 0.85,
            "feedback-" + callbackCount.incrementAndGet()
    );
    ScoredCvTailor tailor = (cv, review, templates) -> cv.toBuilder().summary("optimized").build();
    CvOptimizationOrchestrator orchestrator = new CvOptimizationOrchestrator(reviewer, tailor);

    CvOptimizationResult result = orchestrator.optimize(
            CvBO.builder().name("candidate").build(),
            "Java JD",
            List.of(),
            3,
            review -> callbackCount.incrementAndGet()
    );

    assertEquals(2, result.iterations());
    assertTrue(callbackCount.get() >= 2);
}
```

- [ ] **Step 2: 运行 orchestrator 测试，确认当前签名不支持 callback**

Run:
```powershell
mvn.cmd -pl admin "-Dtest=CvOptimizationOrchestratorTest" "-Denforcer.skip=true" test
```

Expected:
- 因方法签名变化或行为不满足而失败。

- [ ] **Step 3: 给 orchestrator 增加 callback 重载**

目标方法签名：

```java
public CvOptimizationResult optimize(
        CvBO cv,
        String jobDescription,
        List<String> referenceTemplates,
        int maxIterations,
        Consumer<CvReview> progressCallback)
```

循环内在 `history.add(review);` 后立即调用：

```java
if (progressCallback != null) {
    progressCallback.accept(review);
}
```

保留无 callback 的旧重载，内部委托到新方法：

```java
return optimize(cv, jobDescription, referenceTemplates, DEFAULT_MAX_ITERATIONS, null);
```

- [ ] **Step 4: 让 `ResumeApplicationService` 透传 callback**

新增重载：

```java
public CvOptimizationResult optimize(
        Long userId,
        Long resumeId,
        String jobDescription,
        Consumer<CvReview> progressCallback)
```

原有无 callback 版本调用新重载并传 `null`。

- [ ] **Step 5: 修改 `ResumeCareerController.optimizeStream()` 成逐轮发送**

目标行为：

```java
careerTaskExecutor.execute(() -> {
    try {
        CvOptimizationResult result = resumeApplicationService.optimize(
                currentUser.getUserId(),
                resumeId,
                requestParam.getJobDescription(),
                review -> emitter.send(SseEmitter.event()
                        .name("ITERATION")
                        .data(Map.of(
                                "score", review.score(),
                                "feedback", review.feedback(),
                                "status", "PROCESSING"
                        )))
        );
        emitter.send(SseEmitter.event().name("COMPLETE").data(result));
        emitter.complete();
    } catch (Exception ex) {
        ...
    }
});
```

- [ ] **Step 6: 调整 controller 测试，验证事件顺序**

做法建议：
- 自定义 `SseEmitter` 子类或包装器记录发送的事件名
- 断言先出现 `START`
- 再出现至少一个 `ITERATION`
- 最后出现 `COMPLETE`

如果当前 `SseEmitter` 不方便拦截，计划内允许通过提取 `EmitterSink` 小接口提升可测性，但不要扩大改动范围。

- [ ] **Step 7: 运行 SSE 相关测试**

Run:
```powershell
mvn.cmd -pl admin "-Dtest=CvOptimizationOrchestratorTest,ResumeCareerControllerTest,ResumeApplicationServiceTest" "-Denforcer.skip=true" test
```

Expected:
- callback 测试 PASS
- `optimizeStream` 不再是历史回放

- [ ] **Step 8: 提交**

```powershell
git add admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/cv/CvOptimizationOrchestrator.java admin/src/main/java/com/hewei/hzyjy/xunzhi/career/resume/application/ResumeApplicationService.java admin/src/main/java/com/hewei/hzyjy/xunzhi/career/api/ResumeCareerController.java admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/cv/CvOptimizationOrchestratorTest.java admin/src/test/java/com/hewei/hzyjy/xunzhi/career/api/ResumeCareerControllerTest.java
git commit -m "feat: stream cv optimization iterations over sse"
```

## Task 4: 补强 JSON Cleaner 与优化配置

**Files:**
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/support/CareerJsonResponseCleaner.java`
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/config/CareerConfiguration.java`
- Create: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/config/CareerOptimizationProperties.java`
- Modify: `admin/src/main/resources/application.yaml`
- Modify: `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AiCvReviewer.java`
- Modify: `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AiCvReviewerTest.java`
- Create: `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/support/CareerJsonResponseCleanerTest.java`

- [ ] **Step 1: 写 JSON cleaner 的失败测试**

```java
@Test
void returnsToolCallPayloadWithoutCleaning() {
    String raw = "<tool_calls><invoke name=\"activate_skill\"></invoke></tool_calls>";
    assertEquals(raw, CareerJsonResponseCleaner.cleanJsonResponse(raw));
}

@Test
void extractsJsonFromTail() {
    String raw = "分析如下\\n说明文字\\n{\"score\":0.82,\"feedback\":\"ok\"}";
    assertEquals("{\"score\":0.82,\"feedback\":\"ok\"}", CareerJsonResponseCleaner.cleanJsonResponse(raw));
}

@Test
void extractsJsonByStrippingPrefix() {
    String raw = "prefix prefix {\"score\":0.75,\"feedback\":\"usable\"}";
    assertTrue(CareerJsonResponseCleaner.cleanJsonResponse(raw).contains("\"score\":0.75"));
}
```

- [ ] **Step 2: 运行 cleaner 测试，确认当前实现覆盖不足**

Run:
```powershell
mvn.cmd -pl admin "-Dtest=CareerJsonResponseCleanerTest" "-Denforcer.skip=true" test
```

Expected:
- tool-call / tail / prefix stripping 场景失败。

- [ ] **Step 3: 按 JobSpark 补齐 cleaner 策略**

新增：

```java
private static boolean isToolCallResponse(String text) { ... }
private static String extractFromJsonLine(String text) { ... }
private static String extractFromTail(String text) { ... }
private static String extractByStrippingPrefix(String text) { ... }
```

并在 `cleanJsonResponse(...)` 最开始插入：

```java
if (isToolCallResponse(cleaned)) {
    return cleaned;
}
```

- [ ] **Step 4: 新增优化配置属性并接入 orchestrator**

`CareerOptimizationProperties.java`：

```java
@Data
@ConfigurationProperties(prefix = "xunzhi-agent.career.optimization")
public class CareerOptimizationProperties {
    private int maxIterations = 3;
    private double scoreGate = 0.8;
}
```

`CareerConfiguration.java`：

```java
@EnableConfigurationProperties({
        XunzhiLangChain4jProperties.class,
        CareerRagProperties.class,
        CareerObservabilityProperties.class,
        CareerStorageProperties.class,
        CareerAsyncTaskProperties.class,
        CareerOptimizationProperties.class
})
```

`application.yaml`：

```yaml
xunzhi-agent:
  career:
    optimization:
      max-iterations: ${XUNZHI_CAREER_OPTIMIZATION_MAX_ITERATIONS:3}
      score-gate: ${XUNZHI_CAREER_OPTIMIZATION_SCORE_GATE:0.8}
```

- [ ] **Step 5: 让 `AiCvReviewer.heuristicScore()` 向四维权重靠齐**

不要只做 token 命中率，改为四块评分汇总：

```java
double technical = ...
double experience = ...
double project = ...
double education = ...
return clamp(technical * 0.35 + experience * 0.30 + project * 0.25 + education * 0.10);
```

本轮不追求完全语义评分，只要 fallback 的结构与主 Prompt 权重一致。

- [ ] **Step 6: 运行稳定性与配置测试**

Run:
```powershell
mvn.cmd -pl admin "-Dtest=CareerJsonResponseCleanerTest,AiCvReviewerTest,CvOptimizationOrchestratorTest" "-Denforcer.skip=true" test
mvn.cmd -pl admin -Pcareer-external-ai "-Dtest=CareerJsonOutputGuardrailAnnotationIT,AgenticCvOptimizationRuntimeIT" "-Denforcer.skip=true" test
```

Expected:
- cleaner 策略全部通过
- external-ai guardrail 继续共享 cleaner 增强收益

- [ ] **Step 7: 提交**

```powershell
git add admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/support/CareerJsonResponseCleaner.java admin/src/main/java/com/hewei/hzyjy/xunzhi/career/config/CareerConfiguration.java admin/src/main/java/com/hewei/hzyjy/xunzhi/career/config/CareerOptimizationProperties.java admin/src/main/resources/application.yaml admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AiCvReviewer.java admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AiCvReviewerTest.java admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/support/CareerJsonResponseCleanerTest.java
git commit -m "feat: harden career json cleaning and optimization config"
```

## 计划自检

### Spec coverage

- 运行时边界：Task 1 覆盖
- JobSpark Reviewer/Tailor prompt：Task 2 覆盖
- 逐轮 SSE：Task 3 覆盖
- JSON cleaner / heuristic / config：Task 4 覆盖
- 保留简历基础服务：通过 Task 1 的边界切分和 Task 3/4 的局部修改保证，不会重写基础服务

### Placeholder scan

- 已去除 `TBD` / `TODO`
- 每个任务都包含了要修改的文件、测试方向、执行命令和提交策略

### Type consistency

- `CvReview` callback 类型与 orchestrator / service / controller 三层保持一致
- `CareerOptimizationProperties` 与 `application.yaml` 的前缀统一为 `xunzhi-agent.career.optimization`
- Interview 锁 Spring AI 的实现统一依赖 `AiGateway` 与 `CareerSkillRegistry`

## 执行方式

你在目标里已经明确指定使用 `superpowers:subagent-driven-development`，所以后续我会按这个方式执行：每个 Task 派一个新子代理实施，做完后按“规格符合性 -> 代码质量”两段 review，再继续下一个任务。
