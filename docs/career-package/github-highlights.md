# GitHub 项目亮点清单

## 适合放在仓库简介里的描述

```text
An AI Interview Agent platform built with Spring Boot, Spring AI Alibaba, DashScope, Milvus, Redis and MySQL. It supports resume analysis, JD matching, RAG-enhanced question generation, answer evaluation, model-call governance and Prompt observability.
```

中文版本：

```text
基于 Spring Boot、Spring AI Alibaba、DashScope、Milvus、Redis、MySQL 构建的 AI 面试 Agent 平台，支持简历分析、JD 匹配、RAG 增强出题、答案评估、模型调用治理和 Prompt 效果观测。
```

## README 开头可强调的 5 个亮点

```text
- Agent workflow: resume parsing, profile analysis, JD matching, RAG question generation, answer evaluation.
- RAG with Milvus: retrieve similar resume snippets by JD and generate job-specific interview questions.
- Production-minded LLM governance: Spring AI retry, fallback, traceId, audit logs and structured output validation.
- Prompt observability: prompt version registry, model-call audit logs and prompt metrics dashboard.
- Java backend stack: Spring Boot 3, Spring AI Alibaba, MyBatis-Plus, Redis, MySQL, Thymeleaf.
```

## 推荐仓库 Topics

```text
spring-boot
spring-ai
spring-ai-alibaba
dashscope
ai-agent
rag
milvus
redis
mysql
prompt-engineering
java21
interview-assistant
```

## 展示截图建议

如果后续补截图，建议至少包含：

- 首页和上传简历页面
- 简历分析报告页面
- 模拟面试问题页面
- 面试评估报告页面
- Prompt 效果评估看板

## PR / Commit 拆分建议

如果你想把本地改造成一组更清晰的提交，可以按下面拆：

```text
feat(agent): introduce orchestrator, agents and tools
feat(rag): add JD matching and RAG-enhanced question generation
feat(model): use ChatClient with Spring AI retry and business fallback
feat(audit): add prompt versioning and model call audit logs
feat(dashboard): add prompt metrics dashboard
test(parser): strengthen structured output validation tests
docs(career): add resume and interview preparation package
```

## 面试展示顺序

```text
1. 打开 README，说明项目定位和技术栈。
2. 展示 architecture.md，讲 Controller + Orchestrator + Agent + Tool。
3. 展示 prompt-dashboard 页面，说明 Prompt 版本、调用审计和效果评估。
4. 展示 AiModelInvoker，讲框架级重试、业务降级和 traceId。
5. 展示 DTO 校验注解和 AiJsonResponseParser，讲 BeanOutputConverter + Bean Validation。
6. 展示 RagInterviewQuestionAgent，讲 RAG 出题边界和防幻觉约束。
```

## 项目可信度提醒

这个项目适合写成个人自研项目或开源项目，不建议包装成当前公司生产项目。面试中可以强调：

```text
这个项目是我为了转型 AI Agent 应用开发做的完整自研项目，重点补齐 AI 应用落地中的工程链路，包括 RAG、Agent 编排、模型调用治理、Prompt 可观测和结构化输出治理。
```
