# AI 求职顾问

AI 求职顾问是一个面向程序员求职和技术面试训练场景的 AI 面试工作流应用。系统围绕“简历解析、能力画像、岗位匹配、面试题生成、回答评估、报告生成、向量检索”构建完整闭环，适合作为 Java 后端转型 AI 应用开发的展示项目。

## 核心能力

- 简历解析：支持 PDF、DOC、DOCX、TXT，通过 Apache Tika 提取简历文本。
- 简历评分：基于大模型从项目深度、技能匹配、内容完整性、结构清晰度和表达专业性进行多维度评分。
- 基础面试题生成：根据简历中出现的项目和技术栈生成定制化面试题。
- 岗位匹配分析：基于目标岗位说明输出匹配分、能力证据、缺失技能和面试追问方向。
- 岗位定制面试题：结合目标岗位说明、候选人简历和向量检索上下文生成更贴近投递岗位的问题。
- 答案评估：结合简历背景和候选人回答，输出逐题评分、分类得分、优势、不足和参考答案。
- RAG 基础能力：将简历写入 Milvus 向量库，支持基于 JD 或关键词的相似简历检索。
- 会话存储：使用 MySQL 持久化简历、问题和评估结果，使用 Redis 缓存热点面试会话。
- 模型调用治理：基于 Spring AI `ChatClient` 统一模型调用入口，模型侧重试和退避交给 `spring.ai.retry`，业务层只保留 Prompt 版本、调用审计、异常映射、结构化输出强校验和 traceId 日志追踪。
- 调用审计：记录每次大模型调用的 operation、Prompt 版本、traceId、耗时、成功状态和失败原因。
- Prompt 评估：按 Prompt 版本统计成功率、失败数、平均耗时、最大耗时和失败原因分布。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端框架 | Java 21, Spring Boot 3 |
| AI 框架 | Spring AI Alibaba, DashScope |
| 文档解析 | Apache Tika |
| 向量检索 | Milvus Vector Store |
| 数据存储 | MySQL, MyBatis-Plus |
| 缓存 | Redis |
| 页面渲染 | Thymeleaf, Vue 3, HTML, CSS, JavaScript |
| 工程化 | Maven, Docker Compose |

## 开发约束

- 严禁重复封装框架已有能力。Spring AI、Spring AI Alibaba、MyBatis-Plus、Milvus Vector Store、RedisTemplate 已提供的能力必须优先直接使用。
- 新增功能前先检查官方 API 和当前依赖能力，确认框架没有现成方案后，才允许写项目内业务适配代码。
- 项目代码只保留业务编排、参数校验、审计记录、异常映射和页面接口适配，不自研模型重试、工具调度、向量检索、结构化转换等框架能力。
- 如果必须做业务适配，方法注释里要说明“适配的业务边界”，避免后续误解为重复造轮子。

## 工作流架构

项目已从单一 Service 调用模型，改造成 Controller + Service + 模型任务角色 + Tool 分层。这里的 `agent` 包表示“需要大模型推理的任务角色”，整体流程仍由 `InterviewWorkflowService` 确定性编排，不宣称完全自主规划。

```text
InterviewPageController
  -> 返回 Thymeleaf 页面

InterviewApiController
  -> InterviewWorkflowService
      -> ResumeParseTool
      -> ResumeAnalysisAgent
      -> ResumeRepositoryTool
      -> ResumeVectorTool
      -> InterviewQuestionAgent
      -> JobDescriptionMatchAgent
      -> RagInterviewQuestionAgent
      -> AnswerEvaluationAgent
```

详细说明见 [docs/architecture.md](docs/architecture.md)。

## 目录结构

```text
src/main/java/com/xkh/ai/interview
├── config            # MyBatis-Plus、Redis、Trace 配置
├── controller        # 页面入口和 API 入口
├── dto               # 请求、响应和模型结构化输出 DTO
├── entity            # 数据库实体
├── mapper            # MyBatis-Plus Mapper
└── service
    ├── agent         # 需要大模型推理的任务角色
    ├── audit         # 模型调用和对话审计
    ├── llm           # 模型调用、结构化解析和 Prompt 版本
    ├── rag           # Spring AI RAG Advisor 适配
    ├── tool          # AI 顾问和工作流可调用的确定性工具
    └── workflow      # 面试业务流程编排
```

## Prompt 版本管理

每个模型调用场景都有独立 operation 名称和 Prompt 版本号，配置位于 `application.yml`：

```yaml
ai-interview:
  prompt:
    versions:
      resume-analysis: resume-analysis-v2026-05-17-01
      interview-question-generation: interview-question-v2026-05-17-01
      answer-evaluation: answer-evaluation-v2026-05-17-01
      jd-match: jd-match-v2026-05-17-01
      rag-interview-question-generation: rag-question-v2026-05-17-01
```

模型调用审计会将 `operationName` 和 `promptVersion` 一起落库，便于后续评估不同 Prompt 版本的稳定性和效果。可以通过以下接口查看 Prompt 版本维度的调用指标：

```text
GET /api/audit/prompt-metrics?operationName=jd-match&limit=1000
```

指标包括调用总数、成功率、失败数、平均耗时和最大耗时；`avgAttemptCount` 作为早期外层重试审计兼容字段保留。

也可以打开页面查看：

```text
http://localhost:8080/audit/prompt-dashboard
```

## 快速启动

### 1. 准备配置

复制环境变量模板：

```bash
cp .env.example .env
```

将 `.env` 中的 `DASHSCOPE_API_KEY` 替换成你的通义千问 API Key。

应用会通过 `spring.config.import=optional:file:.env[.properties]` 读取当前目录下的 `.env`，Docker Compose 也会复用同一份配置。

模型调用、HTTP 超时和审计参数也可以通过 `.env` 调整：

```text
AI_MODEL_RETRY_MAX_ATTEMPTS=3
AI_MODEL_RETRY_INITIAL_INTERVAL=800ms
AI_MODEL_RETRY_BACKOFF_MULTIPLIER=2
AI_MODEL_RETRY_MAX_INTERVAL=5s
HTTP_CLIENT_CONNECT_TIMEOUT=60s
HTTP_CLIENT_READ_TIMEOUT=600s
HTTP_REACTIVE_CLIENT_CONNECT_TIMEOUT=60s
HTTP_REACTIVE_CLIENT_READ_TIMEOUT=600s
AI_AGENT_AUDIT_ENABLED=true
AI_AGENT_AUDIT_LOG_MESSAGE_CONTENT=true
AI_AGENT_AUDIT_MAX_MESSAGE_CONTENT_LENGTH=4000
```

完整环境变量清单见 [.env.example](.env.example)，本地真实密钥只放在 `.env` 中。

### 2. 启动依赖

```bash
docker compose up -d
```

会启动 MySQL、Redis、Milvus、etcd、MinIO。

确认依赖就绪：

```bash
docker compose ps
docker compose exec mysql sh -c 'mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD"'
docker compose exec redis redis-cli ping
curl http://localhost:9091/healthz
```

`sql/init.sql` 会在 MySQL 数据卷首次初始化时自动执行。若本地已有旧数据卷，需要保留数据并补齐新表结构，请手动执行 `sql/migration-v2-ai-model-call-log.sql` 和 `sql/migration-v3-agent-conversation-message.sql`。

### 3. 启动应用

```bash
mvn spring-boot:run
```

浏览器访问：

```text
http://localhost:8080
```

## API 示例

见 [docs/api-examples.md](docs/api-examples.md)。

## 项目文档

- [深度优化路线图](docs/deep-optimization-roadmap.md)
- [架构说明](docs/architecture.md)
- [API 示例](docs/api-examples.md)
- [面试备注](docs/interview-notes.md)

## 展示流程

1. 打开首页，上传 `samples/java-backend-resume.txt`。
2. 查看简历评分、优势和优化建议。
3. 使用 `samples/java-ai-agent-jd.txt` 调用岗位匹配接口，查看岗位匹配度。
4. 调用“根据岗位生成面试题”接口，生成岗位定制化面试问题。
5. 填写答案并提交。
6. 查看面试评估报告、分类得分和参考答案。
7. 打开 AI 求职顾问页面，围绕当前简历进行追问。
8. 打开 `/audit/prompt-dashboard` 查看 Prompt 效果评估看板。

## 简历可写亮点

```text
AI 求职顾问｜Java 21 / Spring Boot / Spring AI Alibaba / DashScope / Milvus / Redis / MySQL

- 设计并实现“简历解析 -> 能力画像 -> 岗位匹配 -> 面试题生成 -> 回答评估 -> 面试报告生成”的确定性 AI 面试工作流，提升模拟面试的个性化与反馈质量。
- 基于 Spring AI Alibaba 接入通义千问模型，通过 System/User Prompt 分层设计、结构化 JSON 输出约束和容错解析，降低大模型输出不稳定对业务流程的影响。
- 基于 Spring AI `ChatClient` 统一模型调用入口，模型侧重试和退避交给 `spring.ai.retry` 配置，业务层保留 Prompt 版本、调用审计、traceId 日志追踪和模型侧故障映射。
- 强化 AI 结构化输出校验，覆盖必填字段、类型、数值范围、枚举值、数组元素结构和未知字段检测，避免异常模型输出污染业务数据。
- 设计 AI 调用审计表，记录 operation、Prompt 版本、traceId、耗时、成功状态和错误原因，支持模型链路问题回溯。
- 实现 Prompt 指标聚合，支持按版本观察成功率、失败数、平均耗时和失败原因分布，为 Prompt 迭代提供数据依据。
- 使用 Apache Tika 支持多格式简历解析，并围绕项目深度、技能匹配、内容完整性、结构清晰度和表达专业性生成多维度评分。
- 基于 Milvus 构建简历向量知识库，结合目标岗位说明检索相似简历片段，为岗位定制化出题提供 RAG 上下文。
- 实现岗位匹配分析能力，输出岗位匹配分、能力证据、缺失技能、风险点和面试追问方向。
- 设计 MySQL + Redis 的存储与缓存机制，持久化简历评分、面试题和回答评估结果，并对热点会话数据做缓存加速。
```

## 后续规划

- 增加 Prompt 评估看板的时间范围筛选。
- 补充 Demo 数据集和 Prompt/RAG 评测脚本。
