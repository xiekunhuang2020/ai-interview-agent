# AGENT.md

## 项目定位

本项目是“AI 求职顾问 / AI 面试工作流”，不是完全自主规划型 Agent 平台。

核心流程由 `InterviewWorkflowService` 确定性编排，`service.agent` 只表示需要大模型推理的任务角色，`service.tool` 负责文档解析、数据读写、向量检索、缓存等确定性能力。

后续所有改动都要围绕一个目标：让这个项目从“能调用大模型”变成“能被验证、能被回放、能被观测、能被面试追问”的 AI 应用工程。

## 接手项目先读什么

按下面顺序阅读，不要一上来直接改代码：

1. `README.md`：了解产品能力、技术栈、启动方式和演示流程。
2. `docs/architecture.md`：了解 Controller、Workflow、Agent、Tool、RAG、审计的边界。
3. `docs/deep-optimization-roadmap.md`：了解当前优化项、完成状态和开发铁律。
4. `docs/interview-notes.md`：了解项目可讲点和面试追问边界。
5. `src/main/resources/application.yml`：确认模型、Token 预算、RAG、审计和摘要压缩配置。

## 核心代码入口

```text
src/main/java/com/xkh/ai/interview
├── controller    # 页面入口和 API 入口
├── dto           # 请求响应 DTO 和模型结构化输出 DTO
├── entity        # 数据库实体
├── mapper        # MyBatis-Plus Mapper
└── service
    ├── workflow  # 业务流程编排入口
    ├── agent     # 需要模型推理的任务角色
    ├── tool      # 文档解析、数据读写、向量检索、只读顾问工具
    ├── rag       # RAG Advisor、召回评估、评测回放
    ├── llm       # ChatClient 调用、Prompt 版本、结构化输出治理
    └── audit     # 模型调用、Token、音频、顾问消息审计
```

重点入口：

- `InterviewPageController`：只返回页面。
- `InterviewApiController`：只处理 HTTP 入参、响应和错误映射。
- `InterviewWorkflowService`：决定简历上传、诊断、岗位匹配、出题、评估的流程顺序。
- `AiModelCallService`：统一模型调用、Prompt 版本、结构化输出、审计和异常映射。
- `ResumeVectorTool`：简历向量入库。
- `RagInterviewQuestionAgent`：岗位定制出题。
- `InterviewAssistantAgentService`：AI 顾问流式对话。
- `ResumeAgentTools`：AI 顾问可调用的只读工具。

## Harness Engineer 工作方式

这里的 Harness 不是指某个特定商业平台，而是指“评测与验证闭环”的工程思维。

每次做 AI 功能，不只问“模型能不能返回”，还要问：

1. 输入样例是什么？
2. 期望输出结构是什么？
3. 失败时如何分类？
4. 是否能重复运行同一批样例？
5. 是否记录 Token、耗时、模型、Prompt 版本和 traceId？
6. 是否能在运营看板看到成功率、失败原因和成本变化？
7. 是否能解释为什么这次 Prompt/RAG 改动变好或变差？

优先把功能做成可回放、可观测、可比较，再谈“效果更好”。

## 开发铁律

1. 官方方案最高优先。新增能力前先查 Spring AI、Spring AI Alibaba、DashScope、Milvus、MyBatis-Plus、Redis 是否已有封装。
2. 不重复造框架能力。能用 `ChatClient`、Advisor、Evaluator、VectorStore、Converter、Template、Starter 完成的，不写自研替代实现。
3. 业务代码只做业务编排、参数校验、异常映射、状态记录、审计记录和页面适配。
4. 模型失败不能伪造成成功结果。结构化失败、超时、限流、空响应都要明确记录。
5. RAG 内容必须有来源边界。当前简历事实和相似简历参考不能混用。
6. AI 顾问工具只允许只读查询，不允许暴露上传、删除、写库、重新入库等副作用操作。
7. Token 预算和上下文裁剪必须可解释。裁剪策略、预算配置和真实 usage 要能在看板追踪。
8. 新增方法要写清楚用途，方便后续学习和面试复盘。
9. 没有代码、页面、数据或文档证据的能力，不写进 README 和简历。
10. 不为了包装 Agent 而引入重框架。确定性 Workflow 能讲清楚时，不硬上 Graph/自主规划。

## 新增 AI 功能的最小闭环

新增任何模型能力，至少补齐下面几件事：

1. Prompt 文件或 Prompt 版本配置。
2. DTO 结构和 Jakarta Validation 校验。
3. `AiModelCallService` 调用入口。
4. operation 名称和 Prompt 版本映射。
5. `ai_model_call_log` 审计记录。
6. 前端或 API 的明确错误展示。
7. 至少一条可回放样例或演示路径。
8. 文档中说明业务价值、失败边界和验证方式。

## 修改 RAG 的检查清单

改 RAG 前先确认：

1. 是否必须改 RAG，而不是改 Prompt 或业务输入？
2. 是否使用 Spring AI 官方 `VectorStore`、`SearchRequest`、Advisor 或 Document 相关能力？
3. chunk 元数据是否能追溯 `resumeId`、文件名、chunk 序号和来源？
4. 相似简历内容是否只作为参考，不能当成当前候选人的事实？
5. 召回结果是否能在评测回放或看板里观察？

## 修改结构化输出的检查清单

改结构化输出前先确认：

1. DTO 字段是否清晰、必要、可解释？
2. 是否使用 `ChatClient.entity(DTO.class)` 和 Jakarta Validation？
3. 枚举、分数范围、必填字段是否通过注解表达？
4. 失败时是否记录 schema、字段、原因和 traceId？
5. 前端是否展示用户能理解的错误，而不是 Jackson 原始异常堆栈？

## 修改前端页面的检查清单

前端页面不是为了“看起来炫”，而是为了让用户顺着流程完成任务。

重点检查：

1. 当前步骤是否明确？
2. 主按钮是否唯一且含义清楚？
3. 错误是否能指导用户下一步怎么做？
4. 页面是否有横向溢出、大片空白或按钮错位？
5. 移动端是否能完成上传、录音、查看结果和顾问问答？
6. 中文产品不能出现未翻译的英文业务文案。

## 常用验证命令

```powershell
# 启动依赖
docker compose up -d

# 启动后端
mvn spring-boot:run

# 跑演示流程
powershell -ExecutionPolicy Bypass -File scripts/demo-flow.ps1

# 查看项目文档
Get-Content README.md
Get-Content docs/architecture.md
Get-Content docs/deep-optimization-roadmap.md
```

## 最后提醒

这个项目的价值不在“调用了几个模型”，而在于能把 AI 能力接进一个可控业务流程，并且能回答：

- 为什么这样编排？
- 为什么这个结果可信？
- 失败怎么定位？
- 成本怎么观察？
- Prompt/RAG 改动怎么验证？
- 哪些能力是官方框架提供的，哪些是业务代码必须自己做的？

如果一次改动不能让这些问题更容易回答，就先不要加复杂度。
