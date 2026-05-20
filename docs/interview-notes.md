# AI 求职顾问面试备注

这份文档只保留面试和演示时真正需要的讲法。简历正文以外部简历文件为准，本项目内不再维护多份简历包装材料。

## 项目定位

AI 求职顾问是一个面向程序员求职和技术面试训练的 AI 面试工作流系统。它不是完全自主规划型 Agent，而是通过确定性 Workflow 编排简历解析、能力画像、岗位匹配、岗位定制出题、回答评估和 Prompt 观测。

核心讲法：

```text
我没有把它包装成完全自主 Agent，因为求职面试流程有明确顺序。
项目采用 Workflow Service 编排模型任务和 Tool，保证链路可控、结果可追踪、问题可排查。
```

## 30 秒讲法

```text
这个项目是我为了转型 AI 应用开发做的自研项目，场景是 AI 面试训练。
系统把面试训练拆成简历解析、能力画像、岗位匹配、岗位定制出题、答案评估和报告生成几个阶段。
工程上使用 Spring Boot、Spring AI Alibaba、通义千问、Milvus、Redis 和 MySQL，并补了模型调用审计、Prompt 版本管理、结构化输出校验和 RAG 检索能力。
```

## 架构讲法

```text
Page Controller
  -> Thymeleaf 页面

API Controller
  -> Workflow Service
      -> 模型任务角色
          -> AiModelCallService
      -> Tool
          -> MySQL / Redis / Milvus / Tika
```

说明重点：

- Controller 只处理页面入口、HTTP 入参和错误码。
- Workflow Service 负责业务顺序和流程编排。
- 模型任务角色负责简历分析、岗位匹配、出题和评估等推理任务。
- Tool 负责文档解析、存储、缓存和向量检索等确定性能力。

## 容易被问的问题

### 为什么不是普通 ChatBot？

它不是单轮问答，而是围绕面试训练目标拆了多个任务阶段，并且有持久化、RAG、Prompt 版本、调用审计和结果页面。

### 为什么不做完全自主 Agent？

上传简历、分析、匹配、出题、评估这些步骤有明确业务顺序。确定性 Workflow 更适合业务系统落地，能减少不可控行为，也方便排查失败阶段。

### RAG 解决什么问题？

RAG 用在岗位定制面试题生成。系统用目标 JD 检索相似简历片段，让模型参考同类岗位常见追问方向。Prompt 明确要求检索上下文只能作为参考，不能当成当前候选人真实经历。

### 模型调用失败怎么处理？

所有模型调用统一走 Spring AI `ChatClient`。模型侧重试交给 `spring.ai.retry`，业务层只记录 Prompt 版本、调用审计和异常映射。最终失败时记录 `success=0` 和失败原因，接口返回模型网关错误。

### 怎么治理结构化输出？

模型输出先通过 Spring AI `BeanOutputConverter` 转成 DTO，再用 Jakarta Bean Validation 做必填、范围、枚举和级联校验。少量业务规则才留在解析层，例如分数归一化。

## 展示前检查

```bash
mvn -q -DskipTests compile
docker compose up -d
mvn spring-boot:run
```

演示顺序：

1. 打开首页，说明产品定位。
2. 上传 `samples/java-backend-resume.txt`。
3. 查看简历诊断和岗位匹配。
4. 根据岗位生成面试题。
5. 提交答案并查看评估结果。
6. 打开 `/audit/prompt-dashboard` 查看 Prompt 指标和失败原因。

## 简历写法边界

可以写：

```text
基于 Spring Boot、Spring AI Alibaba、DashScope、Milvus、Redis、MySQL 构建 AI 求职顾问，支持简历解析、岗位匹配、岗位定制出题、回答评估、模型调用审计和 Prompt 效果观测。
```

不要写：

```text
精通 AI Agent
完全自主智能体平台
支撑百万用户
准确率提升 90%
```

没有代码、数据或页面证据的内容，不写进简历。
