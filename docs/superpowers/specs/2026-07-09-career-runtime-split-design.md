# AI-Meeting Career Runtime Split Design

> 目标：将 AI-Meeting 中与 JobSpark 融合的 career 能力按职责重新划分运行时边界，明确“简历优化 Agent 使用 LangChain4j、简历基础服务保留 Java/Spring、面试规划与反思锁定 Spring AI”，并以此为基础继续融合 JobSpark 的简历优化能力。

## 1. 背景与问题定义

当前 AI-Meeting 已经吸收了 JobSpark 的部分简历与面试能力，但运行时边界仍然偏混合：

1. 简历优化链路同时保留了 Spring AI 调用路径和 LangChain4j Agent 调用路径。
2. 面试规划链路也保留了 `AgentRuntimeGateway -> LangChain4jAgentAdapter` 的优先尝试逻辑。
3. `career-external-ai` profile 同时为简历优化和面试规划注册了 LangChain4j Agent。
4. JobSpark 中最有迁移价值的部分其实集中在简历优化链，包括：
   - `CvReviewer` 的完整审核评分提示词
   - `ScoredCvTailor` 的完整定制优化提示词
   - Reviewer/Tailor 循环编排
   - 结构化输出防御
   - 逐轮优化进度推送

这导致两个实际问题：

1. 面试链路的职责边界不清，增加了运行时复杂度和维护成本。
2. 简历优化链路尚未充分吸收 JobSpark 的强提示词与用户体验能力，融合收益没有完全兑现。

本设计的目标不是让整个 `career` 域统一切到 LangChain4j，而是进行职责级别的运行时分流。

## 2. 最终目标

本轮设计确认后的唯一目标如下：

1. `简历优化 Agent 能力` 使用 LangChain4j。
2. `简历基础服务` 保留现有 Java/Spring 实现。
3. `面试规划与反思` 全部锁定 Spring AI。
4. 在上述边界之上，继续把 JobSpark 的简历优化能力融入 AI-Meeting。

## 3. 明确的非目标

以下事项不在本轮设计目标内：

1. 不把整个 `career` 域整体迁移到 LangChain4j。
2. 不重写简历上传、解析、RAG、渲染、对象存储、异步任务恢复等基础服务。
3. 不继续增强 Interview 的 LangChain4j 规划能力。
4. 不在本轮恢复 JobSpark 的完整 `@LoopAgent` 原生面试或简历流程。
5. 不在本轮处理 OCR、高保真 PDF/DOCX 后端升级、分布式恢复租约、严格 outbox 等二期能力。

## 4. 运行时边界设计

### 4.1 简历优化链路

简历优化链路定义为以下职责集合：

1. `CvReviewer`
2. `ScoredCvTailor`
3. `CvOptimizationOrchestrator`
4. `ResumeApplicationService.optimize(...)`
5. `ResumeCareerController.optimize(...)`
6. `ResumeCareerController.optimizeStream(...)`

该链路的运行时策略定义为：

1. 优先使用 LangChain4j Agent。
2. `career-external-ai` profile 下提供真实的 LangChain4j Agent Bean。
3. Java 编排器仍由本地 Spring Bean 驱动，不把循环控制完全下放到 Agent 框架。
4. 继续保留 Spring/Java 服务层作为调用外观与业务编排层。

### 4.2 简历基础服务链路

以下能力明确保留现有 Java/Spring 实现：

1. 简历上传与异步解析任务
2. 简历结构化与文本提取
3. 简历持久化
4. RAG chunking / embedding / 检索 / rerank
5. Markdown / HTML / PDF / DOCX 渲染
6. ResumeObjectStorage 与 OSS 适配
7. 任务恢复、取消、重试

换句话说，LangChain4j 只进入“简历优化 Agent 能力”，不吞掉这些基础设施。

### 4.3 面试规划与反思链路

以下能力全部固定使用 Spring AI：

1. JD 对齐
2. 面试阶段规划
3. 首题生成策略
4. 反思与决策
5. 追问建议

这意味着：

1. `InterviewPlanningService` 不再优先尝试 `AgentRuntimeGateway`。
2. `career-external-ai` 中与 interview 相关的 Agent 注册不再作为有效运行路径。
3. 面试链路保留 Spring AI prompt + 本地启发式 fallback 的方式。

## 5. 代码级改动边界

### 5.1 需要改动的核心文件

#### 简历优化主链

1. `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AiCvReviewer.java`
2. `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AiScoredCvTailor.java`
3. `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/cv/CvOptimizationOrchestrator.java`
4. `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/resume/application/ResumeApplicationService.java`
5. `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/api/ResumeCareerController.java`
6. `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/support/CareerJsonResponseCleaner.java`

#### 面试运行时收口

1. `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/agent/interview/InterviewPlanningService.java`
2. 本轮不修改 `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/ai/LangChain4jAgentAdapter.java` 的实现，只通过调用方收口将其使用范围限制在简历优化链路。

#### 配置与资源

1. `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/config/CareerConfiguration.java`
2. 新增 `admin/src/main/java/com/hewei/hzyjy/xunzhi/career/config/CareerOptimizationProperties.java`
3. `admin/src/main/resources/application.yaml`
4. 新增：
   - `admin/src/main/resources/career-skills/cv-reviewer/SKILL.md`
   - `admin/src/main/resources/career-skills/cv-tailor/SKILL.md`

#### external-ai 简历 Agent 对齐

1. `admin/src/career-external-ai/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AgenticCvReviewAgent.java`
2. `admin/src/career-external-ai/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AgenticScoredCvTailorAgent.java`

#### external-ai interview 收口

1. `admin/src/career-external-ai/java/com/hewei/hzyjy/xunzhi/career/config/AgenticInterviewRuntimeConfiguration.java`
2. 本轮保留 interview 相关 Agent interface 与配置文件，但从主链调用处彻底断开，不再作为正式运行路径。

### 5.2 需要新增或调整的测试文件

1. `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/cv/AiCvReviewerTest.java`
2. `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/cv/CvOptimizationOrchestratorTest.java`
3. `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/api/ResumeCareerControllerTest.java`
4. 新增 `admin/src/test/java/com/hewei/hzyjy/xunzhi/career/agent/support/CareerJsonResponseCleanerTest.java`
5. 新增或调整 `career-external-ai` 相关 wiring test，验证仅简历优化链仍可注册 LangChain4j Agent。

## 6. JobSpark 简历优化能力迁移清单

### 6.1 Prompt 精炼

本轮迁移以下 JobSpark 能力：

1. `CvReviewer` 完整 System Prompt
   - 角色定位
   - 四维评分体系：技术 35 / 经验 30 / 项目 25 / 教育 10
   - 评分等级表
   - `strengths / weaknesses / suggestions`
   - 参考模板对标说明

2. `ScoredCvTailor` 完整 System Prompt
   - 角色定位
   - 真实性底线
   - 重写与结构调整策略
   - 技能/经验/项目/教育四维重塑指导
   - 基于 review 的补强策略

3. Prompt 从 Java 硬编码迁出
   - 统一进入 `career-skills`
   - 默认 Spring AI 路径通过 `CareerSkillRegistry` 读取
   - LangChain4j external-ai 路径与其保持内容一致

### 6.2 逐轮进度体验

1. `CvOptimizationOrchestrator` 增加逐轮 `progress callback`
2. `optimizeStream` 改为每轮实时发送 `ITERATION`
3. 保留最终 `COMPLETE`
4. 出错时发送 `ERROR`

### 6.3 结构化输出防御

1. `CareerJsonResponseCleaner` 增加 `tool_call` 检测
2. 增加按行提取策略
3. 增加从尾部反向提取策略
4. 增加逐字符剥离前缀策略

该增强应同时覆盖：

1. 默认 Spring AI 路径中 `AgentResponseParser`
2. `career-external-ai` 中的 `CareerJsonOutputGuardrail`

### 6.4 启发式兜底与配置化

1. `AiCvReviewer.heuristicScore()` 对齐四维权重思路
2. `DEFAULT_MAX_ITERATIONS`
3. `SCORE_GATE`

改造为配置化属性：

1. `xunzhi-agent.career.optimization.max-iterations`
2. `xunzhi-agent.career.optimization.score-gate`

## 7. external-ai 路径的处理原则

这是本设计的一个关键点。

本轮不是删除 `career-external-ai`，而是做“职责收缩”：

1. 保留简历优化相关的 LangChain4j Agent 注册。
2. Interview 相关的 LangChain4j Agent 不再作为正式运行路径。
3. 设计上允许先保留 interview agent interface 与配置文件，但从主链调用处彻底切断。

这样做的好处是：

1. 可以最小化破坏现有 profile 结构。
2. 先把运行时职责边界收干净。
3. Interview external-ai 的彻底删除不在本轮范围内，后续作为独立清理任务处理。

## 8. 风险与防护

### 8.1 Prompt 双份漂移风险

风险：
默认 Spring AI 路径与 external-ai LangChain4j 路径如果各自维护一套 prompt，后续极易漂移。

防护：
本轮要以“同源资源”为目标，至少保证两条简历优化路径语义一致。

### 8.2 Interview 行为回归风险

风险：
从 `InterviewPlanningService` 中移除 LangChain4j 分支后，某些已有测试或运行行为可能发生变化。

防护：
先保留 Spring AI 本地 prompt 与 heuristic fallback，避免运行时能力缺口。

### 8.3 SSE 测试脆弱风险

风险：
逐轮推送改造后，现有 controller 测试只验证“异步触发”，不验证事件顺序。

防护：
补充针对 `ITERATION -> COMPLETE` 顺序的测试。

### 8.4 JSON cleaner 过度清洗风险

风险：
如果没有先判断 tool call，可能把 Agent 框架工具调用响应错误清洗成 JSON。

防护：
先做 `isToolCallResponse()` 检测，再进入 JSON 提取逻辑。

## 9. 测试与验收标准

### 9.1 运行时边界验收

满足以下条件视为边界切分成功：

1. 面试规划与反思主链不再依赖 `AgentRuntimeGateway`
2. 简历优化主链仍可通过 LangChain4j external-ai profile 运行
3. 简历上传、解析、RAG、渲染、存储、异步任务链路保持现有 Spring/Java 实现

### 9.2 功能验收

1. 简历优化使用 JobSpark 强提示词后，仍能正确解析 review/tailor 输出
2. `optimizeStream` 能逐轮实时推送中间 review
3. `CareerJsonResponseCleaner` 能正确处理 tool call、code block、长文本嵌 JSON 等场景
4. optimization 的最大轮次与 score gate 可通过配置调整

### 9.3 回归验收

至少运行以下相关测试集：

1. 简历优化单测
2. `ResumeCareerController` 相关测试
3. `ResumeApplicationService` 相关测试
4. `career-external-ai` 下 CV runtime wiring test
5. InterviewPlanningService 相关测试，验证去除 LangChain4j 主路径后仍正确工作

## 10. 提交策略

建议拆为四组提交，方便 review 与回滚：

1. `refactor: lock interview planning and reflection to spring ai`
2. `feat: migrate jobspark cv reviewer and tailor prompts`
3. `feat: stream cv optimization iterations over sse`
4. `feat: harden career json cleaning and optimization config`

每组提交都应保证：

1. 主题单一
2. 测试可验证
3. 与本设计文档中的一个子目标强关联

## 11. 推荐实施顺序

### 阶段 1：先切运行时边界

目标：
先让系统结构正确，再继续迁 prompt 和体验优化。

内容：

1. Interview 主链彻底锁 Spring AI
2. external-ai interview wiring 收口
3. 简历优化链保留 LangChain4j 主路径

### 阶段 2：迁移 JobSpark 简历优化 Prompt

目标：
先提升输出质量下限。

内容：

1. Reviewer prompt
2. Tailor prompt
3. Prompt 资源文件化

### 阶段 3：恢复逐轮优化体验

目标：
让用户真正看到“逐轮优化”过程。

内容：

1. orchestrator progress callback
2. SSE 实时迭代推送

### 阶段 4：补强稳定性与配置化

目标：
把结构化输出防御与参数管理补齐。

内容：

1. JSON cleaner 增强
2. heuristic score 对齐
3. optimization 配置化

## 12. 结论

本设计采用“按职责分流运行时”的方式继续融合 JobSpark：

1. 让 `简历优化 Agent` 成为 LangChain4j 的专属责任域。
2. 让 `简历基础服务` 继续稳定地停留在 Java/Spring 体系中。
3. 让 `面试规划与反思` 收口到 Spring AI，移除不必要的双轨复杂度。

这是当前 AI-Meeting 与 JobSpark 融合中最稳妥、收益最大、且最符合用户目标的路线。
