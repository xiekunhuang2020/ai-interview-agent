# 简历项目描述

## 项目名称

AI 面试 Agent 平台

## 一句话定位

基于 Spring Boot、Spring AI Alibaba、DashScope、Milvus、Redis、MySQL 实现的岗位定制化 AI 面试训练系统，支持简历解析、能力画像、JD 匹配、RAG 增强出题、回答评估、模型调用治理和 Prompt 效果观测。

## 推荐简历版本

```text
AI 面试 Agent 平台｜Java 17 / Spring Boot / Spring AI Alibaba / DashScope / Milvus / Redis / MySQL

- 设计并实现“简历解析 -> 能力画像 -> JD 匹配 -> RAG 增强出题 -> 回答评估 -> 面试报告生成”的 Agent 工作流，覆盖求职者面试训练闭环。
- 基于 Controller + Orchestrator + Agent + Tool 分层重构业务链路，将模型推理任务与文档解析、数据存储、向量检索等确定性工具解耦。
- 接入 Spring AI Alibaba 与通义千问模型，通过 System/User Prompt 分层设计、Prompt 版本管理和结构化 JSON 输出强校验，提升模型输出可控性。
- 基于 Milvus 构建简历向量知识库，结合目标岗位 JD 检索相似简历片段，为岗位定制化面试题生成提供 RAG 上下文。
- 封装统一模型调用器，支持超时控制、失败重试、线性退避、显式降级、traceId 日志追踪和模型侧故障分层处理。
- 设计 AI 调用审计表与 Prompt 效果评估看板，记录 operation、Prompt 版本、尝试次数、耗时、成功状态、降级状态和失败原因，支持问题回溯与 Prompt 迭代评估。
- 使用 MySQL 持久化简历评分、面试问题和评估结果，使用 Redis 缓存热点面试会话，降低重复读取开销。
```

## 精简版

```text
AI 面试 Agent 平台｜Spring Boot / Spring AI Alibaba / DashScope / Milvus / Redis / MySQL

- 实现简历解析、能力画像、JD 匹配、RAG 增强出题、回答评估和面试报告生成的 Agent 工作流。
- 基于 Orchestrator + Agent + Tool 分层拆分模型推理与确定性工具能力，支持文档解析、向量检索、存储缓存和答案评估。
- 封装统一模型调用器，支持超时、重试、退避、显式降级、traceId 日志追踪和结构化输出强校验。
- 构建 Prompt 版本管理、调用审计和效果看板，按 Prompt 版本统计成功率、降级率、平均耗时和失败原因。
```

## 简历技能栏可补充

```text
- 熟悉 Spring AI Alibaba / DashScope 大模型应用开发，具备 Prompt 分层设计、结构化输出治理和模型调用稳定性处理经验。
- 熟悉 RAG 基础链路，能基于文档解析、Embedding、Milvus 向量检索和上下文增强生成实现业务场景落地。
- 熟悉 AI Agent 工程化设计，能通过 Orchestrator + Agent + Tool 分层组织智能工作流。
- 熟悉模型调用治理，包括超时、重试、退避、降级、traceId、调用审计和 Prompt 版本效果评估。
```

## 不建议写法

```text
- 精通 AI Agent。
- 负责大模型系统开发。
- 支撑百万用户面试训练。
- 提升出题准确率 90%。
```

这些说法要么太泛，要么缺少真实数据支撑。当前项目更适合强调“完整工程链路”和“可观测、可降级、可迭代”的 AI 应用落地能力。
