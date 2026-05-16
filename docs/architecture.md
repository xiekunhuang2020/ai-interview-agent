# AI Interview Agent 架构说明

## 目标定位

本项目从传统的“LLM 接口调用应用”改造成面向求职场景的 AI Agent 应用。系统将模拟面试拆成可编排的任务链路：简历解析、能力画像、问题生成、答案评估、报告生成和向量检索。

## 分层设计

```mermaid
flowchart TD
    U["User / Browser"] --> C["MockInterviewController"]
    C --> O["InterviewAgentOrchestrator"]

    O --> T1["ResumeParseTool"]
    O --> A1["ResumeAnalysisAgent"]
    O --> T2["ResumeRepositoryTool"]
    O --> T3["ResumeVectorTool"]
    O --> A2["InterviewQuestionAgent"]
    O --> A3["AnswerEvaluationAgent"]
    O --> A4["JobDescriptionMatchAgent"]
    O --> A5["RagInterviewQuestionAgent"]

    A1 --> LLM["DashScope Chat Model"]
    A2 --> LLM
    A3 --> LLM
    A4 --> LLM
    A5 --> LLM

    T1 --> Tika["Apache Tika"]
    T2 --> MySQL["MySQL"]
    T2 --> Redis["Redis"]
    T3 --> Milvus["Milvus Vector Store"]
```

## 核心角色

### Controller

只处理 HTTP 入参、响应和错误码，不直接编排业务流程，也不直接调用大模型。

### Orchestrator

`InterviewAgentOrchestrator` 是面试流程编排器，负责决定每一步调用哪个 Agent 或 Tool。

典型流程：

```text
上传简历
  -> ResumeParseTool 解析文件
  -> ResumeAnalysisAgent 生成简历评分与优化建议
  -> ResumeRepositoryTool 持久化结果并写入缓存
  -> ResumeVectorTool 写入向量库
```

### Agent

Agent 负责需要模型推理的任务，目前包含：

- `ResumeAnalysisAgent`：简历评分、能力画像、优化建议
- `InterviewQuestionAgent`：基于简历生成个性化面试题
- `AnswerEvaluationAgent`：结合简历和回答生成面试评估报告
- `JobDescriptionMatchAgent`：分析候选人简历与目标 JD 的匹配度
- `RagInterviewQuestionAgent`：结合 JD、候选人简历和向量检索上下文生成岗位定制题

### Tool

Tool 负责确定性能力，方便后续迁移到函数调用或工具调用框架：

- `ResumeParseTool`：文档解析
- `ResumeRepositoryTool`：MySQL + Redis 读写
- `ResumeVectorTool`：Milvus 向量写入和相似检索

## 当前边界

当前版本采用确定性 Orchestrator 编排 Agent，不做完全自主规划。这样更适合业务系统落地：链路可控、错误边界清楚、易于测试和排查。

## RAG 增强出题流程

```text
输入岗位 JD
  -> ResumeVectorTool 使用 JD 检索相似简历片段
  -> RagInterviewQuestionAgent 组合候选人简历、JD、检索上下文
  -> 生成岗位定制化问题
  -> ResumeRepositoryTool 保存问题到当前会话
```

检索上下文只作为“同类岗位面试深度参考”，不会被当成候选人自己的项目经历。

下一阶段可以继续引入：

- Prompt 版本管理
- 大模型调用重试和降级策略
- JSON Schema 校验
