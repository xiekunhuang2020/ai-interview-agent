# AI 求职顾问深度优化路线图

## 先说结论

当前项目已经能跑通“简历上传、模型分析、岗位匹配、面试题生成、回答评估、AI 顾问流式对话、调用审计”这些流程，但工程深度还不够。站在资深面试官视角，它现在容易被追问成：

```text
你只是把简历和 JD 拼成 Prompt，然后调大模型吧？
RAG 召回效果怎么验证？
Agent 和普通 ChatBot 的区别在哪里？
模型输出不稳定、失败、超时、幻觉怎么治理？
有没有业务状态、指标、评估闭环？
```

后续优化目标不是继续包装简历话术，而是把项目补成“能被深问”的版本。每一项优化必须形成代码证据、页面证据或数据证据。

## 状态说明

| 状态 | 含义 |
| --- | --- |
| 已做 | 已经有代码、页面、数据或文档证据，暂时不用继续投入 |
| 没做 | 还没有开始，后续需要按路线图实现 |
| 不用做 | 当前项目不适合做，做了反而增加复杂度或偏离目标 |
| 还需优化 | 已有雏形，但深度、可讲性或工程证据还不够 |

## 优化项总览

| 序号 | 优化项 | 当前状态 |
| --- | --- | --- |
| 1 | 收敛模型调用层，减少自研味道 | 已做 |
| 2 | 修正项目定位：确定性工作流，不硬吹自主 Agent | 已做 |
| 3 | 增加面试会话状态机 | 已做 |
| 4 | 拆分核心业务表，降低 JSON 大字段依赖 | 已做 |
| 5 | 建设标准化向量入库流程 | 已做 |
| 6 | 增加 RAG 召回评估集 | 已做 |
| 7 | 增加引用来源，抑制幻觉 | 已做 |
| 8 | 将结构化输出规则迁移到 DTO 注解 | 已做 |
| 9 | 增加模型调用错误分类 | 已做 |
| 10 | 做一套 Demo 数据集和演示脚本 | 已做 |
| 11 | 增加效果指标 | 已做 |
| 12 | 增加 Prompt/RAG Evaluation Harness | 已做 |
| 13 | 增加结构化输出失败诊断与官方重试闭环 | 已做 |

## 开发铁律

0. 官方方案最高优先：新增能力、封装工具、写解析器、做基础设施前，必须先查 Spring AI、Spring AI Alibaba、Milvus、MyBatis-Plus、Redis 等官方能力；没有明确结论，不进入编码。
1. 官方能力优先：Spring AI、Spring AI Alibaba、Milvus Vector Store、MyBatis-Plus、RedisTemplate 已提供的能力不重复封装。
2. 业务代码只做业务编排、状态管理、参数校验、异常映射、审计记录和页面接口适配。
3. 新增代码前先写清楚“官方已有 / 官方没有 / 官方不适合”的判断，再决定是否写自定义实现。
4. 能用配置、Starter、Builder、Advisor、Converter、Evaluator、Template 做的，不自研替代实现。
5. 如果 Spring AI 和 Spring AI Alibaba 都提供同类能力，优先选择更贴近 DashScope/通义生态、代码更少且不明显增加复杂度的 Spring AI Alibaba 封装。
6. Graph、Agent Framework、Admin 等重能力必须按项目复杂度引入；当前确定性流程能讲清楚时，不为了包装项目硬上。
7. 简历只写已经落地的能力。没有代码、数据、页面或文档证据的点，不写到简历。
8. 每个新增方法必须写清楚方法用途，方便后续学习和面试复盘。

## 第一阶段：先把“只是 Prompt 调用”降到最低

### 1. 收敛模型调用层，减少自研味道

**状态：已做**

**早期问题**

项目早期有旧模型调用类和手写兜底响应，容易被问成“为什么不直接用 Spring AI 的 ChatClient、Converter 和 Retry 配置？”

**优化目标**

让模型调用看起来像“使用 Spring AI 官方能力”，而不是“自己包了一套模型网关”。

**改造内容**

- 已将模型调用入口收敛为 `AiModelCallService`。
- 底层统一使用 Spring AI `ChatClient`，重试和退避交给 `spring.ai.retry`。
- 删除手写兜底响应工厂，不再生成没有真实模型结果的兜底 JSON。
- 业务层只保留 Prompt 版本、调用审计、异常映射和 traceId 日志。
- 给模型调用链路加清晰注释：哪些是官方能力，哪些是业务边界。

**验收标准**

- 面试时能明确说出：重试、流式、工具调用、结构化转换分别由哪个官方能力完成。
- 代码里没有自研 retry/backoff、线程池调度、通用模型网关这类重复能力。

**面试可讲**

我没有重复造模型调用框架，底层统一使用 Spring AI `ChatClient`。项目层只保留 Prompt 版本、业务审计、异常映射和 traceId，模型最终失败后直接记录失败并返回 502。

### 2. 修正项目定位：确定性工作流，不硬吹自主 Agent

**状态：已做**

**早期问题**

如果把整个项目说成自主 Agent，面试官会追问规划、反思、多步决策、状态流转。当前代码更像“确定性业务工作流 + 局部工具调用”，继续包装成完全自主 Agent 容易被深问打穿。

**优化目标**

把项目定位改准确：这是面向求职场景的 AI 应用工作流，包含局部工具调用和模型推理能力，但不是完全自主 Agent 平台。

**改造内容**

- README、架构文档、简历项目描述统一改成“AI 求职顾问 / AI 面试工作流”。
- 保留 `agent` 包表示模型推理任务角色，但明确它不是自主规划器。
- `InterviewWorkflowService` 明确承担业务顺序和流程编排。
- 首页和 AI 顾问页面去掉“智能体平台”式表达，改成更贴近产品的 AI 顾问/工作流表述。

**验收标准**

- 文档、代码包名、简历描述不再互相打架。
- 能回答“为什么不用完全自主 Agent”：求职流程有明确顺序，确定性工作流更可控。

**面试可讲**

这个项目没有盲目追求完全自主 Agent，而是采用确定性 Workflow 编排 AI 能力。求职场景的上传、分析、匹配、出题、评估都有明确顺序，用工作流能保证步骤可控、结果可追踪、问题可排查。

## 第二阶段：补真正的业务状态和流程深度

### 3. 增加面试会话状态机

**状态：已做**

**早期问题**

现在主要靠 `resume_info` 存 JSON 字段，缺少明确业务状态。面试官追问“流程怎么控制、重复提交怎么处理、失败后怎么恢复”时，证据不足。

**优化目标**

引入会话状态，让流程从“几个接口串起来”变成“有状态的业务闭环”。

**改造内容**

- 已新增 `interview_session` 表。
- 当前状态：

```text
UPLOADED
ANALYZED
JD_MATCHED
QUESTIONS_GENERATED
ANSWER_SUBMITTED
EVALUATED
FAILED
```

- 上传、诊断、岗位匹配、出题、答案提交和复盘评估都会更新状态。
- 未生成题目时提交答案仍会返回明确错误。
- 核心阶段失败时记录 `FAILED`、失败阶段和失败原因。
- 前端工作台页面展示当前流程状态。

**验收标准**

- 页面能展示当前进度。
- 数据库能追踪每份简历走到哪个阶段。
- 失败后能知道卡在哪一步。

**面试可讲**

我把 AI 流程做成有状态工作流，避免只是接口串联。每个阶段都有状态、失败原因和恢复边界，便于排查线上问题。

### 4. 拆分核心业务表，降低 JSON 大字段依赖

**状态：已做**

**早期问题**

评分、问题、评估结果大量存在 JSON 字段中，演示可以，但工程上不利于检索、统计和追踪。

**优化目标**

保留 JSON 快速展示能力，同时把关键业务结果拆成可查询表。

**改造内容**

- 已新增或拆分：

```text
resume_score
interview_question
interview_evaluation
jd_match_result
```

- `resume_info` 只保留基础简历信息，不再保留结果 JSON 字段。
- `ResumeRepositoryTool` 写入和读取都走拆分表，避免新旧双轨代码影响阅读。
- 岗位匹配结果写入 `jd_match_result`，不再只依赖浏览器本地缓存。
- 评估明细、参考答案等复杂列表暂保留 JSON，避免过度拆表导致代码膨胀。

**验收标准**

- 可以按 resumeId 查询所有问题。
- 可以统计题目类型分布、平均评分、岗位匹配分。
- 简历项目描述中可以真实写“结构化存储与统计”。

**面试可讲**

早期为了快速闭环用了 JSON 字段，后续我把高频查询和统计字段拆表，兼顾页面展示和后续数据分析。

## 第三阶段：把 RAG 从“能搜”补成“可评估”

### 5. 建设标准化向量入库流程

**状态：已做**

**现状问题**

现在更像把简历文本直接写入向量库，缺少分段、元数据、清洗和质量控制。

**优化目标**

让 RAG 有工程过程：解析、清洗、分段、元数据、入库、可追踪。

**改造内容**

- 已使用 Spring AI Alibaba `RecursiveCharacterTextSplitter` 对简历文本切片后再写入 Milvus。
- 已移除未使用的 MiniMax embedding 配置，统一使用 DashScope `text-embedding-v3`。
- 已在入库前清洗多余空白。
- 已给每个 chunk 标记 `resumeId`、`fileName`、`chunkIndex`、`chunkCount`、`indexedAt`，便于检索结果溯源。
- 已在 AI 顾问工具返回和 RAG 注入上下文中展示 chunk 来源。
- 后续继续使用 Spring AI 文档/向量相关官方能力。
- 暂不硬编码识别 `section`，后续如果需要精准段落，可用模型结构化解析结果反哺向量入库。
- 暂不补向量入库记录表；当前上传日志、chunk 元数据和 RAG 召回评估已经能支撑学习和演示，避免为了记录而扩表。

**验收标准**

- Milvus 中能按 `resumeId` 过滤。
- 上传日志能看到写入了多少 chunk。
- 相似检索结果能看到来源 chunk。

**面试可讲**

我不是直接把整份简历扔进向量库，而是先做文本清洗，再用 Spring AI Alibaba 的 splitter 切片，并给 chunk 加 resumeId、fileName、chunkIndex 等元数据，便于过滤和溯源。

### 6. 增加 RAG 召回评估集

**状态：已做**

**现状问题**

RAG 如果只有“相似简历检索”，面试官会问召回准不准。没有评估数据，就只能空讲。

**优化目标**

构建小规模评估集，用数据说明召回质量。

**改造内容**

- 已新增 `samples/eval/rag-recall-cases.json`，先放 10 条查询样例。
- 每条样例标注 `expectedKeywords`，用关键词命中判断 TopK 是否召回到有效片段。
- 已新增 `RagRecallEvaluationService`，读取评估集并批量执行向量检索。
- 已新增接口：

```text
GET /api/evaluation/rag-recall?topK=5
```

- 接口输出：
  - TopK 命中率
  - 平均召回耗时
  - 未命中样例
- 评估前需要先上传 `samples/java-backend-resume.txt` 或包含对应关键词的简历，让 Milvus 中有可召回数据。

**验收标准**

- 能用一条接口跑出评估结果。
- 能看到每条样例的命中关键词、召回片段、耗时和未命中列表。
- 该能力只作为轻量本地回归，不替代完整线上效果评测。

**面试可讲**

我给 RAG 做了小规模离线评估，不只看能不能搜到，而是看 TopK 命中率、耗时和失败样例，再反向调整 chunk 和 metadata。

### 7. 增加引用来源，抑制幻觉

**状态：已做**

**现状问题**

模型生成岗位定制题时，如果没有来源信息，容易把相似简历内容误写成当前候选人经历。

**优化目标**

让 RAG 输出可追溯，模型回答必须区分“当前简历事实”和“相似样本参考”。

**改造内容**

- RAG 上下文中已带上来源类型：

```text
CURRENT_RESUME_FACT
SIMILAR_RESUME_REFERENCE
```

- `InterviewQuestionsDTO.Question` 已增加 `evidenceSource` 和 `sourceNote`。
- `InterviewQuestionsDTO.Question` 会归一化来源字段，防止模型漏填或输出别名导致结构化失败。
- `interview_question` 表已增加 `evidence_source` 和 `source_note` 字段。
- RAG Advisor 注入的检索上下文会明确标记 `SIMILAR_RESUME_REFERENCE`。
- AI 顾问工具 `search_similar_resumes` 返回 `sourceType` 和 `sourceName`。
- 前端模拟面试页面已展示“当前简历事实 / 相似简历参考”。

**验收标准**

- 页面能看到问题来源。
- Prompt 中明确禁止把相似简历当成当前候选人事实。
- 数据库能保存每道题的来源类型和来源说明。

**面试可讲**

我把 RAG 上下文分为事实证据和参考证据，避免模型把别人的项目写到当前候选人身上。

## 第四阶段：补结构化输出和稳定性治理证据

### 8. 将结构化输出规则迁移到 DTO 注解

**状态：已做**

**现状问题**

如果大量规则写在自定义 JSON parser 里，容易像手工 if 校验，也容易和 Spring AI 官方结构化输出能力重复。

**优化目标**

用 Jakarta Validation 注解表达结构约束，只保留少量业务归一化逻辑。

**改造内容**

- 已检查并改造 `ResumeScoreResultDTO`、`InterviewQuestionsDTO`、`JobDescriptionMatchResultDTO`、`InterviewEvaluationDTO`。
- 已用注解表达：
  - 必填
  - 长度
  - 分数范围
  - 枚举正则
  - 嵌套对象校验
- `ResumeScoreResultDTO.ScoreDetail` 自己裁剪评分维度范围，避免模型偶发输出 10 分制外的值。
- `InterviewQuestionsDTO.Question` 自己归一化题目类型和来源类型，兼容 `SYSTEM_DESIGN`、`RAG`、中文来源等模型别名。
- 已删除自定义 `AiJsonResponseParser`。
- `AiModelCallService` 统一调用 Spring AI 官方 `ChatClient.entity(DTO.class)` 转换结构化结果。
- `AiModelCallService` 只补充 Jakarta Validation、审计记录和异常映射，不再承担 JSON parser 职责。

**验收标准**

- 大部分校验能从 DTO 注解看懂。
- 项目不再维护自定义 JSON parser，各业务 DTO 的字段范围和枚举约束由注解表达。
- `mvn -q -DskipTests compile` 通过。

**面试可讲**

我没有把结构化输出治理写成一堆 if，而是用 DTO 注解声明约束，再由 Jakarta Validation 统一校验。

### 9. 增加模型调用错误分类

**状态：已做**

**现状问题**

现在失败原因可能只是一段错误文本，不利于统计。

**优化目标**

把失败分成可统计的类型。

**建议分类**

```text
TIMEOUT
RATE_LIMIT
MODEL_ERROR
STRUCTURED_OUTPUT_ERROR
VALIDATION_ERROR
EMPTY_RESPONSE
UNKNOWN
```

**改造内容**

- `ai_model_call_log` 已增加 `error_type`。
- 已新增 `migration-v8-ai-model-error-type.sql`。
- `AiModelCallAuditRecorder` 写入失败审计时会自动分类：

```text
TIMEOUT
RATE_LIMIT
MODEL_ERROR
STRUCTURED_OUTPUT_ERROR
VALIDATION_ERROR
EMPTY_RESPONSE
UNKNOWN
```

- 运营看板“失败原因”已改为按 `errorType` 聚合，并保留一条错误文本样例用于排查。
- 最近模型调用记录会展示失败类型。
- 原始 `errorMessage` 继续保留，便于定位具体异常。

**验收标准**

- 失败原因看板能显示错误类型分布。
- 模型空响应会被识别为 `EMPTY_RESPONSE`，不再继续进入结构化解析。
- `mvn -q -DskipTests compile` 通过。

**面试可讲**

我没有只记录异常文本，而是把模型失败分类，方便判断是超时、限流、结构化输出失败还是业务校验失败。

## 第五阶段：补业务价值和可演示数据

### 10. 做一套 Demo 数据集和演示脚本

**状态：已做**

**现状问题**

没有稳定样例，面试演示容易翻车。

**优化目标**

准备固定简历、JD、答案和预期效果，保证 3 分钟可演示。

**改造内容**

- `samples` 下已准备 3 份简历：
  - `java-backend-resume.txt`
  - `ai-application-resume.txt`
  - `platform-backend-resume.txt`
- `samples` 下已准备 3 份 JD：
  - `java-ai-agent-jd.txt`
  - `java-backend-performance-jd.txt`
  - `fullstack-ai-product-jd.txt`
- 已新增 `samples/interview-answers-demo.json`，用于固定提交答案。
- 已新增 `scripts/demo-flow.ps1`，自动串起上传简历、岗位匹配、岗位出题、提交答案和 RAG 召回评估。
- README 和 API 示例已补充脚本用法。
- 页面已支持“填入样例 JD”。

**验收标准**

- 新机器拉代码后能按 README 完整演示。
- 演示流程不会依赖临时手写输入。

**面试可讲**

我准备了固定 Demo 数据集，用于验证从简历分析到岗位匹配、出题、评估的完整链路。

### 11. 增加效果指标

**状态：已做**

**现状问题**

业务价值没有数字，听起来像玩具项目。

**优化目标**

用可量化指标证明项目效果。

**已展示指标**

```text
模型调用样本量
模型调用成功率
模型平均耗时
模型失败次数
向量召回命中率
简历解析耗时
岗位匹配耗时
面试题生成成功率
```

**改造内容**

- 运营看板顶部新增“效果指标”区。
- 指标来自模型调用审计、失败原因聚合和向量召回评估。
- 页面可按调用场景、提示词版本、样本量和召回数量筛选。
- 向量召回评估在页面刷新时执行，失败时不影响其他审计指标展示。
- 不再要求通过命令行脚本查看第 11 项结果。

**验收标准**

- 能展示至少 5 个真实指标。
- 简历描述可以写“可观测、可评估”，而不是空话。

**面试可讲**

我把 AI 调用做成可观测链路，能看到成功率、失败率、耗时和 RAG 召回效果，用数据驱动 Prompt 和检索策略调整。

### 12. 增加 Prompt/RAG Evaluation Harness

**状态：已做**

**现状问题**

Prompt 改完以后，只能靠人工体验判断好坏；RAG 生成题目是否贴合岗位、是否基于上下文，也缺少稳定评估方法。这样面试官一问“你怎么证明 Prompt 版本变好了”，就很难回答。

**优化目标**

建设轻量 Evaluation Harness，用固定样例集批量回归简历分析、JD 匹配和岗位出题效果，让 Prompt 优化从“凭感觉调”变成“有样例、有指标、有失败样例”的工程流程。

**改造内容**

- 已新增 `samples/eval/prompt-rag-evaluation-cases.json`，准备简历分析、JD 匹配、RAG 出题样例。
- 使用 Spring AI 官方 `RelevancyEvaluator` 判断输出是否贴合上下文。
- 使用 Spring AI 官方 `FactCheckingEvaluator` 判断输出事实是否有简历和岗位上下文支撑。
- 结构化输出仍由业务 DTO 转换和校验负责，不在 Harness 中额外堆业务规则。
- 对每条样例记录场景、评估器、评分、通过状态、耗时和说明。
- 运营看板新增“评测回放”区域，点击“运行评测”后展示报告。
- 输出评测报告，包含：

```text
样例数量
结构化输出成功率
上下文相关性通过率
事实一致性通过率
评估器与评分
平均耗时
失败说明
```

- 暂不引入 Alibaba AssistantAgent / Admin 这类重平台，避免当前项目大材小用。

**验收标准**

- 能在运营看板中点击按钮跑完整评测集。
- 至少覆盖简历分析、JD 匹配、RAG 出题三个场景。
- 能看到官方评估器、评分、通过率和失败说明。
- 评测报告在页面展示，不依赖命令行输出。

**面试可讲**

我给 Prompt 和 RAG 做了轻量 Evaluation Harness。每次改 Prompt 后，会用固定样例集回归结构化成功率，并通过 Spring AI 官方 RelevancyEvaluator 和 FactCheckingEvaluator 检查上下文相关性、事实一致性和失败样例，避免只凭主观感觉判断效果。

### 13. 增加结构化输出失败诊断与官方重试闭环

**状态：已做**

**现状问题**

上传简历时偶尔会出现：

```text
AI 输出不符合 resume-score 结构：无法转换为目标 DTO
```

这说明模型调用本身成功了，但返回内容没有稳定符合 `ResumeScoreResultDTO` 的 DTO 结构。当前结构化转换已经改为 Spring AI 官方 `ChatClient.entity(DTO.class)`，失败会抛出 `AiStructuredOutputException`，前端能看到结构、字段、原因和 traceId。

**可能原因**

- 模型返回了 Markdown 代码块、说明文字或多个 JSON 片段。
- JSON 字段名、层级或类型与 DTO 不一致。
- 分数、数组元素、嵌套对象结构不符合约束。
- 简历内容较长时，模型输出被截断或格式漂移。
- 当前错误信息隐藏了底层转换异常，失败样例无法沉淀复盘。

**优化目标**

把“偶发结构化输出失败”从单次报错变成可诊断、可回放、可用官方能力重试的工程闭环。

**已完成**

- 官方能力判断：Spring AI 已提供 `StructuredOutputValidationAdvisor`，可按 DTO JSON Schema 校验输出并自动重试；Spring AI Alibaba DashScope 已提供 `responseFormat(JSON_OBJECT)`，可约束模型返回 JSON；失败样例持久化不属于框架能力，复用现有审计表。
- `AiStructuredOutputException` 已携带 `schemaName`、`fieldPath`、`failureReason`。
- 结构化模型调用已接入 Spring AI 官方 `StructuredOutputValidationAdvisor`，失败时由官方 Advisor 追加错误信息重试一次。
- 结构化模型调用已接入 DashScope `responseFormat(JSON_OBJECT)`，减少模型返回 Markdown 或解释文本的概率。
- Bean Validation 失败会返回字段路径和具体约束原因，例如 `scoreDetail.expressionScore`。
- JSON 转 DTO 交给 Spring AI 官方 `ChatClient.entity(DTO.class)`，不再保留自定义 parser。
- 结构化转换或 DTO 校验失败时，会记录 `STRUCTURED_OUTPUT_ERROR` 审计记录。
- 前端错误提示会展示结构、字段、原因和 traceId，便于直接排查。
- 运营看板已增加“结构化失败样例”，复用 `ai_model_call_log` 展示最近失败记录、调用场景、traceId、耗时和失败原因。

**暂不自研**

- 不再新增自定义 JSON 修复 Prompt，避免绕开 Spring AI 官方结构化输出链路。
- 不手写 `MARKDOWN_WRAPPED`、`FIELD_MISSING`、`TYPE_MISMATCH` 等细分分类，除非后续看板里真实失败样例足够多，且 Spring AI 官方错误信息无法支撑排查。
- 失败样例先在运营看板沉淀；是否纳入 Evaluation Harness，等真实样例稳定后再判断。

**验收标准**

- 前端不再只看到“无法转换为目标 DTO”，而能看到字段、原因和 traceId。
- Prompt 看板能统计结构化输出失败次数。
- 运营看板能持续沉淀结构化失败样例，用于后续 Prompt 回归。
- 可修复格式错误优先交给 Spring AI 官方 `StructuredOutputValidationAdvisor` 重试一次，重试后仍失败再返回明确错误。

**面试可讲**

大模型结构化输出不是 100% 稳定的。我没有自己写 JSON 修复器，而是用 DashScope `responseFormat(JSON_OBJECT)` 先约束返回格式，再用 Spring AI 官方 `StructuredOutputValidationAdvisor` 按 DTO JSON Schema 校验并重试一次；最终失败会记录到审计表和运营看板，方便按 traceId 复盘。

## 执行顺序

后续按这个顺序逐项完成：

```text
1. 收敛模型调用层，减少自研封装
2. 修正文档和简历定位，避免过度宣称
3. 增加 interview_session 状态机
4. 拆分核心业务表
5. 标准化 RAG 入库流程
6. 增加 RAG 召回评估集
7. 增加 RAG 引用来源
8. DTO 注解化结构校验
9. 模型错误分类
10. Demo 数据集和演示脚本
11. 效果指标看板
12. Prompt/RAG Evaluation Harness
13. 结构化输出失败诊断与官方重试闭环
```

## 简历更新规则

每完成一项，再同步更新简历和面试材料。未完成前不写进简历。

| 完成项 | 简历可写程度 |
| --- | --- |
| 只写了 Prompt | 不写亮点，只能写“大模型接口接入” |
| 有状态工作流 | 可写“业务流程编排和状态治理” |
| 有 RAG 评估 | 可写“RAG 召回评估和调优” |
| 有错误分类和指标 | 可写“模型调用可观测和稳定性治理” |
| 有 Demo 数据集 | 可写“可演示、可验证的 AI 应用闭环” |
| 有 Evaluation Harness | 可写“Prompt/RAG 评测回归和版本迭代” |
| 有结构化失败诊断闭环 | 可写“结构化输出校验、官方重试和失败样例回放” |

## 下一阶段优化方向

下面这些方向暂时不写进简历核心项目职责，等代码、页面或数据证据落地后再同步更新。继续遵循“官方方案最高优先”：先查 Spring AI、Spring AI Alibaba、DashScope 官方能力，再决定是否写业务适配代码。

## 下一阶段状态总览

| 序号 | 优化项 | 当前状态 |
| --- | --- | --- |
| A | 模型调用成本观测 | 已做 |
| B | Prompt/RAG 上下文预算控制 | 已做 |
| B+ | Token 级上下文预算和裁剪观测 | 已做 |
| C | 顾问对话历史摘要压缩 | 已做 |
| D | 语音面试陪练 | 已做 |
| E | 岗位截图识别 | 已做 |
| F | 复盘到 AI 顾问闭环 | 已做 |
| G | 评估失败后的前端恢复 | 已做 |

### A. 模型调用成本观测

**优先级：高**

**状态：已做**

**为什么重要**

企业落地 AI 应用时，模型调用成本会直接影响产品是否可持续。成本主要来自输入 token、输出 token、Embedding、结构化重试和多轮对话上下文。这个方向能体现“AI 工程化不是只会调接口，还要关注成本和可控性”。

**规划内容**

- 已确认 Spring AI `ChatResponseMetadata.getUsage()` 和 Spring AI Alibaba `DashScopeAiUsage` 能拿到官方 token usage。
- 已将结构化调用改为 `responseEntity`、普通调用改为 `chatResponse`，保留 `ChatResponse` metadata。
- 已新增 `model_name`、`input_tokens`、`output_tokens`、`total_tokens` 字段，并将模型名称和 usage 写入 `ai_model_call_log`。
- 运营看板已按 operation、Prompt 版本展示调用模型、总 Token、平均 Token 和最近模型调用 Token。
- 运营看板已区分输入 Token、输出 Token、总 Token 和平均 Token。
- AI 顾问 SSE 流式对话已改用 Spring AI 官方 `stream().chatResponse()` 获取 metadata，并写入模型调用审计。
- 已在运营看板补充按模型单价、输入/输出 Token 和 ASR 音频时长换算的估算费用。
- 估算费用只作为优化参考，真实账单仍以百炼控制台为准。
- 结合现有成功率、耗时和失败原因，形成“效果 + 稳定性 + 成本”的观测闭环。

**暂不做**

- 不做用户账单系统。
- 不做复杂成本预测。
- 不虚构真实金额，除非已接入模型价格配置和真实 usage 数据。

### B. Prompt/RAG 上下文预算控制

**优先级：高**

**状态：已做**

**为什么重要**

简历全文、JD、RAG 上下文和对话历史如果无限拼接，会导致 token 成本上升、响应变慢，也会增加模型抓错重点的概率。

**规划内容**

- 已增加 `PromptContextBudgetService`，统一控制简历、JD、回答、AI 顾问输入、RAG 文档和工具返回的 Token 预算。
- 已接入 Spring AI 官方 `TokenCountEstimator` / `JTokkitTokenCountEstimator`，不再用固定字符数近似预算上限。
- 简历超预算时保留开头和结尾，避免只截尾导致后半段项目经历、教育经历等信息整体丢失。
- 已将预算配置放到 `application.yml`，支持通过环境变量调整，不在业务代码里写死预算值。
- RAG 检索结果通过 Spring AI 官方 `DocumentPostProcessor` 扩展点裁剪，再交给官方 `RetrievalAugmentationAdvisor` 注入上下文。
- 模型调用审计已记录最终 Prompt 字符数、是否裁剪和被裁剪字符数。
- 模型调用审计已记录 operation 级输入 Token 预算、超预算数量、是否超预算和是否“超预算但未裁剪”。
- 运营看板已展示平均输入长度、上下文裁剪次数、累计减少字符数、输入预算超出次数，并在最近模型调用中展示每次调用的 Token、预算、裁剪和耗时。

**已有基础**

- AI 顾问工具已做只读边界和配置化返回裁剪。
- 面试题查询最多返回前 10 题。
- RAG chunk 已有元数据，可按片段来源追踪。

### C. 顾问对话历史摘要压缩

**优先级：中**

**状态：已做**

**为什么重要**

多轮对话越长，历史消息越占上下文。简单截断会丢失用户目标，全部保留又浪费 token。摘要压缩能在成本和上下文连续性之间折中。

**规划内容**

- 已使用 Spring AI 官方 `MessageWindowChatMemory` 管理最近消息窗口，替代手写 `Deque` 历史。
- 当顾问对话超过固定消息数后，使用模型生成一份会话摘要。
- 后续 Prompt 携带“会话摘要 + 最近几轮消息 + 当前问题”，避免无限塞历史。
- 摘要内容限定为用户目标、已确认事实、关键建议和待跟进事项。
- 摘要调用走统一 `AiModelCallService`，会进入模型调用审计和 Token 预算看板。
- 摘要结果写入顾问消息审计，当前运行期摘要保存在内存中，不额外增加长期记忆表。

**风险**

- 摘要本身也会消耗 token。
- 摘要错误可能污染后续回答，因此需要把摘要视为“辅助上下文”，不能覆盖当前简历真实数据。

### D. 语音面试陪练

**优先级：中高**

**状态：已做**

**为什么重要**

文字答题和真实面试仍有差距。语音输入能让模拟面试更接近真实场景，也能扩展表达能力评估维度。

**规划内容**

- 已完成第一轮：前端在每道面试题旁提供“录音回答”，录音结束后转成 WAV 提交后端。
- 已完成第一轮：后端使用 DashScope 官方 Java SDK `Recognition` 和 `paraformer-realtime-v2` 转写文本。
- 已完成第一轮：转写文本回填答案框，用户检查后仍走原有回答评估链路。
- 已完成第一轮：语音转写调用写入 `ai_model_call_log`，记录 operation、模型、耗时、成功状态和失败原因。
- 已增加语音表达建议：语速、停顿、重复词、表达完整度。
- 已在复盘报告中展示语音作答题数、语音平均分、优先复盘题和复述建议。
- 已在运营看板展示 ASR 音频文件大小、采样率、时长和估算费用。
- 后续如果继续扩展，可补更细的停顿检测和语速统计，但不做视频/表情分析。

**暂不做**

- 不做视频面试分析。
- 不做人脸、表情、眼神等高隐私能力。
- 不把语音表达分数伪装成客观结论，只作为训练建议。

### E. 岗位截图识别

**优先级：中高**

**状态：已做**

**为什么重要**

真实用户经常直接保存 BOSS、拉勾、猎聘等招聘软件截图。如果能上传截图自动提取岗位说明，会明显降低输入成本，也能让产品不只停留在“粘贴文本”。

**规划内容**

- 已确认官方能力：Spring AI 提供 `UserMessage + Media` 多模态消息抽象，Spring AI Alibaba DashScope 支持 `qwen-vl-plus`、`qwen-vl-max` 等视觉模型。
- 已完成第一阶段：上传岗位截图，使用 Spring AI 官方多模态消息和 DashScope 视觉模型提取文本。
- 已显式设置 `multiModel=true` 和 `messageFormat=IMAGE`，保证请求进入 DashScope 多模态接口。
- 识别结果已进入岗位说明输入框，用户确认后再触发岗位匹配。
- 对识别结果做最小清洗：去掉按钮、时间、电量、招聘软件界面噪音。
- 页面提示用户人工检查识别结果后再提交。

**暂不做**

- 不做复杂版式还原。
- 不保存原始截图，除非后续明确加入隐私和存储策略。
- 不绕开官方 OCR 能力自己训练模型。
- 不做简历截图识别，避免和现有 PDF、DOC、DOCX、TXT 简历解析链路重复。

### 推荐推进顺序

```text
1. 成本观测：已完成普通、结构化和 AI 顾问 SSE 流式调用。
2. 上下文预算：给 Prompt/RAG/工具返回建立统一预算边界。
3. 岗位截图识别：已完成，降低用户输入成本。
4. 语音面试陪练：已完成录音转写、人工确认、语音建议和复盘总览。
5. 顾问对话摘要：已完成会话摘要压缩，避免多轮对话无限堆历史。
```

### 本轮 20 项收敛清单

| 序号 | 优化项 | 状态 |
| --- | --- | --- |
| 1 | 复盘摘要一键复制 | 已做 |
| 2 | 复盘摘要一键带到 AI 顾问 | 已做 |
| 3 | AI 顾问接收待发送复盘 Prompt | 已做 |
| 4 | AI 顾问快捷问题 | 已做 |
| 5 | 模拟面试已答/未答/语音进度 | 已做 |
| 6 | 定位第一道未答题 | 已做 |
| 7 | 录音后人工确认转写结果 | 已做 |
| 8 | 重新录音和仅保留文字 | 已做 |
| 9 | 提交评估失败后保留答案 | 已做 |
| 10 | 答案草稿本地临时恢复 | 已做 |
| 11 | 回答评估结构化失败治理 | 已做 |
| 12 | 语音表达建议进入逐题反馈 | 已做 |
| 13 | 语音复盘总览 | 已做 |
| 14 | ASR 音频元信息审计 | 已做 |
| 15 | 输入/输出 Token 分开统计 | 已做 |
| 16 | 输入预算与策略状态展示 | 已做 |
| 17 | 场景级估算调用成本 | 已做 |
| 18 | 单次调用估算费用提示 | 已做 |
| 19 | README 同步产品亮点 | 已做 |
| 20 | 路线图同步完成状态 | 已做 |

## 面试防翻车口径

如果现在就被问深，可以诚实回答：

```text
这个项目第一版确实是从 Prompt + 大模型调用起步的。
后面我没有继续堆提示词，而是按工程化方向补了几块：
一是把流程做成可追踪的 Workflow；
二是用 Spring AI 官方能力做结构化输出、工具调用、RAG 和流式响应；
三是补调用审计、错误分类和效果指标；
四是给 RAG 和 Prompt 做离线评测 Harness，避免只凭感觉说检索和提示词有效。
```

这套口径必须跟实际代码进度一致，不能提前吹。

