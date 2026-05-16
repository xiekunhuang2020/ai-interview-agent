# AI Interview Agent Platform

AI Interview Agent Platform 是一个面向求职者和技术面试训练场景的智能面试 Agent 应用。系统围绕“简历解析、能力画像、个性化出题、回答评估、报告生成、向量检索”构建完整闭环，适合作为 Java 后端转型 AI Agent 应用开发的展示项目。

## 核心能力

- 简历解析：支持 PDF、DOC、DOCX、TXT，通过 Apache Tika 提取简历文本。
- 简历评分：基于大模型从项目深度、技能匹配、内容完整性、结构清晰度和表达专业性进行多维度评分。
- 个性化出题：根据简历中出现的项目和技术栈生成定制化面试题。
- 答案评估：结合简历背景和候选人回答，输出逐题评分、分类得分、优势、不足和参考答案。
- RAG 基础能力：将简历写入 Milvus 向量库，支持基于 JD 或关键词的相似简历检索。
- 会话存储：使用 MySQL 持久化简历、问题和评估结果，使用 Redis 缓存热点面试会话。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端框架 | Java 17, Spring Boot 3 |
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

## 快速启动

### 1. 准备配置

复制环境变量模板：

```bash
cp .env.example .env
```

将 `.env` 中的 `DASHSCOPE_API_KEY` 替换成你的通义千问 API Key。

应用会通过 `spring.config.import=optional:file:.env[.properties]` 读取当前目录下的 `.env`，Docker Compose 也会复用同一份配置。

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

## 演示流程

1. 打开首页，上传 `samples/java-backend-resume.txt`。
2. 查看简历评分、优势和优化建议。
3. 进入模拟面试，生成个性化面试问题。
4. 填写答案并提交。
5. 查看面试评估报告、分类得分和参考答案。
6. 通过 `/api/rag/search` 测试简历向量检索。

## 简历可写亮点

```text
AI 面试 Agent 平台｜Java 17 / Spring Boot / Spring AI Alibaba / DashScope / Milvus / Redis / MySQL

- 设计并实现“简历解析 -> 能力画像 -> 个性化出题 -> 回答评估 -> 面试报告生成”的 Agent 工作流，提升模拟面试的个性化与反馈质量。
- 基于 Spring AI Alibaba 接入通义千问模型，通过 System/User Prompt 分层设计、结构化 JSON 输出约束和容错解析，降低大模型输出不稳定对业务流程的影响。
- 使用 Apache Tika 支持多格式简历解析，并围绕项目深度、技能匹配、内容完整性、结构清晰度和表达专业性生成多维度评分。
- 基于 Milvus 构建简历向量知识库，支持相似简历检索和 JD 匹配场景扩展。
- 设计 MySQL + Redis 的存储与缓存机制，持久化简历评分、面试题和回答评估结果，并对热点会话数据做缓存加速。
```

## 后续规划

- 增加 JD 匹配 Agent，输出岗位匹配度和风险点。
- 增加 RAG 增强出题 Agent，结合 JD、简历片段和历史面试题生成问题。
- 增加模型调用重试、超时控制和降级策略。
- 增加 JSON Schema 校验和 Prompt 版本管理。
- 补充单元测试、集成测试和接口文档。
