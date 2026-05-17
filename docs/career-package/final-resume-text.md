# 最终版简历文本

这份文本可以直接放进你的简历，再根据真实工作经历、教育经历和目标岗位做少量调整。建议把项目标注为“个人自研项目”或“开源实践项目”。

## 个人优势

```text
10 年 Java 后端开发经验，熟悉 Spring Boot、MySQL、Redis、MyBatis-Plus 等主流后端技术栈，具备业务系统设计、接口性能优化和工程化治理经验。近期重点转向 AI Agent 应用开发，围绕 Spring AI Alibaba、DashScope、RAG、Milvus、Prompt 工程、模型调用治理和结构化输出校验完成 AI 面试 Agent 平台实践，能够将大模型能力与传统后端系统进行工程化集成。
```

## 专业技能

```text
- Java 后端：熟悉 Java 21、Spring Boot、MyBatis-Plus、RESTful API 设计和常见后端分层架构，能够完成业务系统从接口设计到持久化落地的开发工作。
- 数据库与缓存：熟悉 MySQL 表结构设计、索引优化、事务和慢 SQL 排查；熟悉 Redis 缓存设计、热点数据缓存和会话状态缓存。
- AI 应用开发：熟悉 Spring AI Alibaba、DashScope 大模型接入、Prompt 分层设计、结构化 JSON 输出约束和模型调用异常处理。
- Agent 工程化：理解 Orchestrator + Agent + Tool 分层设计，能够将模型推理任务与文档解析、存储、缓存、向量检索等确定性工具解耦。
- RAG 检索增强：熟悉文档解析、向量写入、Milvus 相似度检索和上下文增强生成流程，能够围绕业务场景设计 RAG 链路。
- 稳定性治理：具备 Spring AI 框架级模型重试配置、显式降级、traceId 日志追踪、调用审计和 Prompt 版本效果评估实践经验。
- 工程工具：熟悉 Maven、Docker Compose、Git，能够编写项目运行文档、接口示例和基础测试用例。
```

## 项目经历

### AI 面试 Agent 平台

```text
项目描述：
基于 Spring Boot、Spring AI Alibaba、DashScope、Milvus、Redis、MySQL 构建的岗位定制化 AI 面试训练系统，支持简历解析、能力画像、岗位匹配、岗位定制出题、回答评估、模型调用治理和 Prompt 效果观测。

技术栈：
Java 21、Spring Boot 3、Spring AI Alibaba、DashScope、Milvus、Redis、MySQL、MyBatis-Plus、Apache Tika、Thymeleaf、Vue 3、Docker Compose

项目职责：
- 设计并实现“简历解析 -> 能力画像 -> 岗位匹配 -> 岗位定制出题 -> 回答评估 -> 面试报告生成”的 Agent 工作流，覆盖求职者面试训练完整链路。
- 基于 Controller + Orchestrator + Agent + Tool 分层重构业务架构，将模型推理任务与文档解析、数据存储、缓存和向量检索等确定性能力解耦。
- 接入 Spring AI Alibaba 与通义千问模型，通过 System/User Prompt 分层设计、Prompt 版本管理、`BeanOutputConverter` 和 Jakarta Bean Validation 提升模型输出可控性。
- 基于 Apache Tika 支持 PDF、DOC、DOCX、TXT 简历解析，并将简历文本写入 MySQL、Redis 和 Milvus，支撑持久化、热点会话缓存和向量检索。
- 构建基于 Milvus 的简历向量知识库，结合目标岗位说明检索相似简历片段，为岗位定制化面试题生成提供 RAG 上下文。
- 基于 Spring AI `ChatClient` 统一模型调用入口，模型侧重试和退避交给 `spring.ai.retry` 配置，业务层保留显式降级、traceId 日志追踪和模型侧故障分层处理。
- 强化 AI 结构化输出治理，基于严格 DTO 转换和 Jakarta Bean Validation 覆盖必填字段、数值范围、枚举值、数组元素结构和未知字段检测，避免异常模型输出污染业务数据。
- 设计 AI 调用审计表与 Prompt 效果评估看板，记录 operation、Prompt 版本、耗时、成功状态、降级状态和失败原因，支持问题回溯与 Prompt 迭代评估。

项目成果：
- 完成从普通 LLM 应用到 AI Agent 工程化应用的架构改造，形成 Agent 编排、RAG 检索、模型治理、Prompt 观测和结构化输出校验的完整闭环。
- 沉淀可复用的大模型调用治理能力，为后续扩展备用模型、Function Calling、Prompt A/B 测试和评估看板打下基础。
```

## 项目一句话讲法

```text
这是一个我为了转型 AI Agent 应用开发做的自研项目，重点不是简单调大模型接口，而是围绕真实业务流程补齐 Agent 编排、RAG、模型调用稳定性、Prompt 版本观测和结构化输出治理。
```

## 投递岗位标题建议

```text
Java 后端开发工程师 / AI 应用开发方向
AI Agent 应用开发工程师
Java AI 应用开发工程师
大模型应用开发工程师
```
