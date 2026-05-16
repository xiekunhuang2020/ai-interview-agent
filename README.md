# AI Interview Agent Platform

AI Interview Agent Platform 是一个面向求职者和技术面试训练场景的智能面试 Agent 应用。系统围绕“简历解析、能力画像、个性化出题、回答评估、报告生成、向量检索”构建完整闭环，适合作为 Java 后端转型 AI Agent 应用开发的展示项目。

## 核心能力

- 简历解析：支持 PDF、DOC、DOCX、TXT，通过 Apache Tika 提取简历文本。
- 简历评分：基于大模型从项目深度、技能匹配、内容完整性、结构清晰度和表达专业性进行多维度评分。
- 个性化出题：根据简历中出现的项目和技术栈生成定制化面试题。
- JD 匹配分析：基于目标岗位 JD 输出匹配分、能力证据、缺失技能和面试追问方向。
- RAG 增强出题：结合 JD、候选人简历和向量检索上下文生成岗位定制化面试问题。
- 答案评估：结合简历背景和候选人回答，输出逐题评分、分类得分、优势、不足和参考答案。
- RAG 基础能力：将简历写入 Milvus 向量库，支持基于 JD 或关键词的相似简历检索。
- 会话存储：使用 MySQL 持久化简历、问题和评估结果，使用 Redis 缓存热点面试会话。
- 模型调用治理：统一封装大模型调用，支持超时、重试、退避、结构化输出强校验和 traceId 日志追踪。
- 调用审计：记录每次大模型调用的 operation、Prompt 版本、traceId、尝试次数、耗时、成功状态和失败原因。
- 降级与评估：模型最终失败后可返回显式降级结果，并按 Prompt 版本统计成功率、降级率、平均耗时和平均重试次数。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端框架 | Java 21, Spring Boot 3 |
| AI 框架 | Spring AI Alibaba, DashScope |
| 文档解析 | Apache Tika |
| 向量检索 | Milvus Vector Store |
| 数据存储 | MySQL, MyBatis-Plus |
| 缓存 | Redis |
| 页面渲染 | Thymeleaf, HTML, CSS, JavaScript |
| 工程化 | Maven, Docker Compose |

## Agent 架构

项目已从单一 Service 调用模型，改造成 Controller + Orchestrator + Agent + Tool 分层。

```text
Controller
  -> InterviewAgentOrchestrator
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
├── agent             # 需要大模型推理的任务角色
├── config            # MyBatis-Plus、Redis 配置
├── controller        # HTTP 入口
├── entity            # 数据库实体
├── mapper            # MyBatis-Plus Mapper
├── orchestrator      # Agent 工作流编排
├── service/dto       # 业务 DTO
├── support           # 通用支持能力
└── tool              # 确定性工具能力
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

指标包括调用总数、成功率、降级率、平均耗时、最大耗时和平均尝试次数。

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

模型调用治理参数也可以通过 `.env` 调整：

```text
AI_MODEL_MAX_ATTEMPTS=3
AI_MODEL_FALLBACK_ENABLED=true
AI_MODEL_TIMEOUT_SECONDS=60
AI_MODEL_BACKOFF_MILLIS=800
AI_MODEL_EXECUTOR_POOL_SIZE=4
```

### 2. 启动依赖

```bash
docker compose up -d
```

会启动 MySQL、Redis、Milvus、etcd、MinIO。

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

## 面试材料

- [最终版简历文本](docs/career-package/final-resume-text.md)
- [简历项目描述](docs/career-package/resume-project.md)
- [面试讲稿](docs/career-package/interview-script.md)
- [技术难点 FAQ](docs/career-package/technical-faq.md)
- [GitHub 项目亮点清单](docs/career-package/github-highlights.md)
- [提交拆分建议](docs/career-package/commit-plan.md)
- [展示前检查清单](docs/career-package/showcase-checklist.md)

## 演示流程

1. 打开首页，上传 `samples/java-backend-resume.txt`。
2. 查看简历评分、优势和优化建议。
3. 使用 `samples/java-ai-agent-jd.txt` 调用 JD 匹配接口，查看岗位匹配度。
4. 调用 RAG 增强出题接口，生成岗位定制化面试问题。
5. 填写答案并提交。
6. 查看面试评估报告、分类得分和参考答案。
7. 通过 `/api/rag/search` 测试简历向量检索。
8. 打开 `/audit/prompt-dashboard` 查看 Prompt 效果评估看板。

## 简历可写亮点

```text
AI 面试 Agent 平台｜Java 21 / Spring Boot / Spring AI Alibaba / DashScope / Milvus / Redis / MySQL

- 设计并实现“简历解析 -> 能力画像 -> 个性化出题 -> 回答评估 -> 面试报告生成”的 Agent 工作流，提升模拟面试的个性化与反馈质量。
- 基于 Spring AI Alibaba 接入通义千问模型，通过 System/User Prompt 分层设计、结构化 JSON 输出约束和容错解析，降低大模型输出不稳定对业务流程的影响。
- 封装统一模型调用器，支持调用超时、失败重试、线性退避、显式降级和 traceId 日志追踪；无可用降级时将模型侧故障映射为 502。
- 强化 AI 结构化输出校验，覆盖必填字段、类型、数值范围、枚举值、数组元素结构和未知字段检测，避免异常模型输出污染业务数据。
- 设计 AI 调用审计表，记录 operation、Prompt 版本、traceId、尝试次数、耗时、成功状态和错误原因，支持模型链路问题回溯。
- 实现显式降级策略和 Prompt 指标聚合，支持按版本观察成功率、降级率、平均耗时和平均尝试次数，为 Prompt 迭代提供数据依据。
- 使用 Apache Tika 支持多格式简历解析，并围绕项目深度、技能匹配、内容完整性、结构清晰度和表达专业性生成多维度评分。
- 基于 Milvus 构建简历向量知识库，结合 JD 检索相似简历片段，为岗位定制化出题提供 RAG 上下文。
- 实现 JD 匹配 Agent，输出岗位匹配分、能力证据、缺失技能、风险点和面试追问方向。
- 设计 MySQL + Redis 的存储与缓存机制，持久化简历评分、面试题和回答评估结果，并对热点会话数据做缓存加速。
```

## 后续规划

- 增加 Prompt 评估看板的时间范围筛选。
- 补充集成测试。
