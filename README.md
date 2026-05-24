# AI 求职顾问

作者：谢半仙

AI 求职顾问是一套面向求职者和技术面试训练场景的 AI 面试工作流应用。系统围绕“上传简历 -> 简历诊断 -> 岗位匹配 -> 岗位定制出题 -> AI 顾问问答 -> 回答评估 -> 运营观测”构建闭环，用于验证 Java 后端工程如何接入大模型、RAG、结构化输出和可观测能力。

项目定位不是完全自主规划型 Agent，而是更适合业务落地的确定性 AI 工作流：主流程由 `InterviewWorkflowService` 控制，`agent` 包表示需要大模型推理的任务角色，`tool` 包负责文档解析、数据读写、缓存和向量检索等确定性能力。

## 在线体验

- 产品演示地址：[http://150.109.232.238/](http://150.109.232.238/)
- 运营看板：`/audit/prompt-dashboard`
- 代码开源地址：[https://github.com/xiekunhuang2020/ai-interview-agent](https://github.com/xiekunhuang2020/ai-interview-agent)

## 核心能力

- 简历解析：支持 PDF、DOC、DOCX、TXT，使用 Spring AI Tika Document Reader 提取文本。
- 简历诊断：从项目深度、技能匹配、内容完整、结构清晰、表达质量等维度生成评分和建议。
- 岗位匹配：根据目标 JD 输出匹配分、命中技能、能力缺口、投递风险和学习建议。
- 岗位定制出题：结合当前简历、目标 JD 和向量检索上下文生成面试题。
- AI 顾问问答：基于只读工具查询简历画像、面试题和相似简历片段，支持 SSE 流式输出。
- 回答评估：结合简历背景、面试题和用户答案生成逐题评分、反馈和参考答案。
- 运营观测：按 operation 和 Prompt 版本统计成功率、失败原因、耗时和结构化失败样例。
- 评测回放：使用固定样例集回放简历分析、岗位匹配和 RAG 出题效果。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3、MyBatis-Plus |
| AI 框架 | Spring AI、Spring AI Alibaba、DashScope |
| 结构化输出 | `ChatClient.entity(DTO.class)`、`StructuredOutputValidationAdvisor`、Jakarta Validation |
| RAG | Spring AI RAG、Milvus Vector Store、DashScope Embedding |
| 文档解析 | Spring AI Tika Document Reader、Apache Tika |
| 存储 | MySQL、Redis |
| 前端 | Vue 3、Thymeleaf、HTML、CSS |
| 部署 | Docker Compose、Linux、Nginx |

## 工程亮点

### 1. 确定性工作流，而不是普通 ChatBot

系统将面试准备拆成多个可追踪任务节点：简历解析、简历诊断、岗位匹配、RAG 出题、回答评估和 AI 顾问问答。每个节点由 Workflow 编排，模型只参与需要推理的环节，避免把业务链路完全交给模型自由发挥。

### 2. 官方能力优先，减少自研封装

项目遵循“官方方案最高优先”的开发准则：

- 模型调用统一使用 Spring AI `ChatClient`
- 结构化转换使用 `ChatClient.entity(DTO.class)`
- 结构化输出重试使用 Spring AI `StructuredOutputValidationAdvisor`
- JSON 输出约束使用 DashScope `responseFormat(JSON_OBJECT)`
- 向量检索使用 Spring AI `VectorStore` 和 `SearchRequest`
- RAG 注入使用 Spring AI 官方 Advisor 机制

业务代码只保留流程编排、参数校验、异常映射、审计记录和页面接口适配。

### 3. 结构化输出治理

大模型结构化输出并不稳定，项目采用三层治理：

1. DashScope `responseFormat(JSON_OBJECT)` 约束模型返回 JSON。
2. Spring AI `StructuredOutputValidationAdvisor` 按 DTO JSON Schema 校验输出，并自动重试一次。
3. `ChatClient.entity(DTO.class)` 转换为 DTO 后，再用 Jakarta Validation 校验必填、范围、枚举和嵌套结构。

结构化失败会记录为 `STRUCTURED_OUTPUT_ERROR`，前端展示 schema、字段、原因和 traceId，运营看板展示最近失败样例。

### 4. RAG 可追踪、可评估

简历入库流程使用 Spring AI Alibaba `RecursiveCharacterTextSplitter` 切片，并为每个 chunk 增加 `resumeId`、`fileName`、`chunkIndex`、`chunkCount`、`indexedAt` 等元数据。RAG 出题时区分：

- `CURRENT_RESUME_FACT`：当前候选人真实简历事实
- `SIMILAR_RESUME_REFERENCE`：相似简历参考片段

Prompt 明确要求相似简历只能作为追问方向参考，不能当成当前候选人的真实经历。运营看板提供 RAG 召回评估，展示 TopK 命中率、平均耗时和未命中样例。

### 5. Prompt/RAG Evaluation Harness

项目内置固定评测样例集，覆盖简历分析、岗位匹配和 RAG 出题。运营看板可一键运行回放，并通过 Spring AI 官方 Evaluator 输出：

- 结构化输出成功率
- 上下文相关性
- 事实一致性
- 失败检查点
- 平均耗时

这让 Prompt 调整从“凭感觉”变成“有样例、有指标、有失败记录”的迭代流程。

### 6. 模型调用审计

`ai_model_call_log` 记录每次模型调用的 operation、Prompt 版本、模型名称、traceId、耗时、Token 用量、Prompt 字符数、裁剪字符数、成功状态、错误类型和失败原因。运营看板支持按场景和 Prompt 版本查看：

- 模型调用样本量
- 调用模型名称
- 成功率
- 平均耗时
- 输入 Token、输出 Token、总 Token 与平均 Token
- 平均输入长度、上下文裁剪次数和累计减少字符数
- 失败原因分布
- 结构化失败样例
- 最近模型调用日志

## 架构概览

```text
InterviewPageController
  -> 返回页面

InterviewApiController
  -> InterviewWorkflowService
      -> ResumeParseTool
      -> ResumeRepositoryTool
      -> ResumeVectorTool
      -> ResumeAnalysisAgent
      -> JobDescriptionMatchAgent
      -> InterviewQuestionAgent
      -> RagInterviewQuestionAgent
      -> AnswerEvaluationAgent

InterviewAssistantAgentService
  -> ResumeAgentTools
      -> get_resume_profile
      -> get_resume_interview_questions
      -> search_similar_resumes
```

目录结构：

```text
src/main/java/com/xkh/ai/interview
├── config        # MyBatis-Plus、Redis、Trace、ToolCalling 配置
├── controller    # 页面入口和 API 入口
├── dto           # 请求响应 DTO 和模型结构化输出 DTO
├── entity        # 数据库实体
├── mapper        # MyBatis-Plus Mapper
└── service
    ├── agent     # 需要大模型推理的任务角色
    ├── audit     # 模型调用和顾问消息审计
    ├── llm       # ChatClient 调用、结构化输出、Prompt 版本
    ├── rag       # RAG Advisor、召回评估、评测回放
    ├── tool      # 文档解析、向量入库、只读顾问工具
    └── workflow  # 面试业务流程编排
```

详细说明见 [docs/architecture.md](docs/architecture.md)。

## 快速启动

### 1. 准备环境变量

复制 `.env.example`：

```powershell
Copy-Item .env.example .env
```

至少需要配置：

```text
DASHSCOPE_API_KEY=你的通义千问 API Key
```

应用通过 `spring.config.import=optional:file:.env[.properties]` 读取本地配置，Docker Compose 也复用同一份 `.env`。

### 2. 启动依赖

```powershell
docker compose up -d
```

依赖包括 MySQL、Redis、Milvus、etcd、MinIO。首次启动会自动执行 `sql/init.sql`。

若已有旧数据卷，需要按顺序执行 `sql/migration-v2` 到 `sql/migration-v10`。

### 3. 启动后端

```powershell
mvn spring-boot:run
```

访问：

```text
http://localhost:8080
```

## 演示流程

1. 打开首页，进入“候选人导入”。
2. 上传简历并填写目标岗位说明。
3. 查看简历诊断和岗位匹配结果。
4. 生成岗位定制面试题。
5. 进入模拟面试，填写答案并生成评估报告。
6. 进入 AI 顾问，围绕当前简历进行流式问答。
7. 打开运营看板，查看调用审计、RAG 召回评估和 Prompt/RAG 评测回放。

也可以运行脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/demo-flow.ps1
```

## API 示例

见 [docs/api-examples.md](docs/api-examples.md)。

## 项目文档

- [架构说明](docs/architecture.md)
- [深度优化路线图](docs/deep-optimization-roadmap.md)
- [API 示例](docs/api-examples.md)
- [面试备注](docs/interview-notes.md)
- [样例数据说明](samples/README.md)

## 后续优化方向

后续不继续堆概念，优先围绕真实企业场景补能力：

- 成本观测：已打通 Spring AI / DashScope 官方 token usage 落库，并在运营看板按 operation 展示输入 token、输出 token、总 token 和模型名称。
- 上下文预算：已为 Prompt、RAG 上下文、AI 顾问工具返回和对话历史设置最大预算，降低 token 成本和无关上下文干扰。
- 对话摘要：当 AI 顾问多轮对话变长时，保留“会话摘要 + 最近消息”，减少历史消息无限拼接。
- 语音面试陪练：通过官方 ASR 能力把语音回答转写成文本，复用现有回答评估链路，并补充语速、停顿和表达完整度建议。
- 截图 OCR：支持 JD 截图、招聘软件截图、简历截图 OCR，降低用户输入成本，再复用岗位匹配和简历诊断流程。

这些方向会继续遵循官方能力优先原则，不为了炫技自研 OCR、ASR、token 估算或多模态模型封装。

## 面试口径

这个项目的核心不是“我调了一个大模型接口”，而是把大模型能力放进一个可控的后端业务流程里：

- 流程可控：Workflow 编排每个业务节点。
- 输出可校验：DTO、JSON Schema Advisor、Jakarta Validation 多层校验。
- 检索可追踪：RAG chunk 带元数据和引用来源。
- 效果可回放：固定样例集评估 Prompt/RAG 结果。
- 问题可排查：模型调用审计、错误分类、traceId 和运营看板。

当前版本不是完全自主规划型 Agent，也没有宣称真实生产用户量、QPS 或业务准确率。它的价值在于展示 Java 后端工程如何将 LLM、RAG、结构化输出、审计和前端体验组合成一个可演示、可追问、可迭代的 AI 应用。
