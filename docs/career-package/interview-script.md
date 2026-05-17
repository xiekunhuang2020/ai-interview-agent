# 面试讲稿

## 30 秒版本

这个项目是我为了系统化学习 AI Agent 应用开发做的一个自研项目，场景是 AI 面试训练。它不是简单的 ChatBot，而是把面试训练拆成简历解析、能力画像、岗位匹配、岗位定制出题、答案评估和报告生成几个阶段。工程上我用了 Spring Boot、Spring AI Alibaba、通义千问、Milvus、Redis 和 MySQL，并且补了模型调用治理，比如框架级重试、业务降级、traceId、Prompt 版本管理、调用审计和 Prompt 效果看板。

## 2 分钟版本

这个项目的定位是“岗位定制化 AI 面试 Agent 平台”。用户上传简历后，系统会先通过 Apache Tika 解析 PDF、DOC、DOCX 或 TXT 文件，然后由 `ResumeAnalysisAgent` 生成简历评分和优化建议，并把简历文本持久化到 MySQL、缓存到 Redis，同时写入 Milvus 向量库。

在面试训练阶段，系统有两种出题模式。第一种是基础面试题，直接根据候选人简历生成问题。第二种是岗位定制面试题，用户输入目标岗位说明后，系统会用岗位说明去 Milvus 检索相似简历片段，再由 `RagInterviewQuestionAgent` 结合候选人简历、岗位说明和检索上下文生成更贴近投递岗位的问题。候选人提交答案后，`AnswerEvaluationAgent` 会输出逐题评分、分类得分、优势、不足和参考答案。

架构上我没有把所有逻辑堆在 Service 里，而是拆成 Controller、Orchestrator、Agent 和 Tool。Controller 只处理 HTTP，`InterviewAgentOrchestrator` 负责编排流程，Agent 处理需要模型推理的任务，Tool 处理文档解析、存储、缓存和向量检索等确定性能力。

我重点补了 AI 应用工程化能力。所有模型调用都经过 Spring AI `ChatClient`，模型侧重试和退避交给 Spring AI 的 `spring.ai.retry` 配置；业务层的 `AiModelInvoker` 只保留 Prompt 版本、调用审计、显式降级和 traceId 日志追踪。模型输出先由 Spring AI `BeanOutputConverter` 转成 DTO，再用 Jakarta Bean Validation 做必填、范围、枚举和级联校验，少量业务规则比如简历分项分数归一化才保留在代码里。每次模型调用还会记录 operation、Prompt 版本、耗时、成功状态、降级状态和失败原因，并提供 Prompt 效果看板观察成功率、降级率和平均耗时。

## 架构讲法

```text
Controller
  -> Orchestrator
      -> Agent
          -> AiModelInvoker
      -> Tool
          -> MySQL / Redis / Milvus / Tika
```

可以这样解释：

```text
我把系统拆成两类能力：需要模型推理的 Agent，和结果确定的 Tool。
比如简历评分、岗位匹配、岗位定制出题和答案评估属于 Agent；
文档解析、数据库读写、缓存读写和向量检索属于 Tool。
Orchestrator 负责编排这些 Agent 和 Tool，避免 Controller 直接堆业务逻辑，也方便后续替换模型、扩展工具或增加新的面试流程。
```

## RAG 讲法

```text
这个项目里的 RAG 不是单纯“把文档塞进 prompt”，而是围绕岗位定制面试这个业务目标设计的。
简历上传时会写入 Milvus，后续用户输入目标 JD，系统会用 JD 做语义检索，拿到相似简历片段作为参考上下文。
RAG Prompt 明确约束：检索上下文只能作为同类岗位面试深度参考，不能当成当前候选人的真实经历，避免模型把别人的项目张冠李戴。
```

## 模型治理讲法

```text
AI 应用落地时，模型调用不稳定是高频问题，所以我把模型调用统一收敛到 AiModelInvoker。
这里没有重复造模型重试轮子：底层调用走 Spring AI Alibaba 的 ChatClient 和 RetryTemplate，业务层只处理 Prompt 版本、审计、显式降级和 traceId 日志追踪。
如果模型最终失败，系统可以返回结构合法的降级 JSON，但审计表会记录 success=0、fallbackUsed=1。
这样业务可以继续走，但观测层不会把降级伪装成成功。
```

## 结构化输出讲法

```text
大模型输出 JSON 最大的问题不是“完全不可解析”，而是半对半错。
比如字段存在但类型错，分数越界，枚举值不在范围内，数组元素结构不对。
所以我没有只做 JSON parse，也没有把所有规则写成手工 if 判断；而是用 Spring AI `BeanOutputConverter` 做严格 DTO 转换，用 Jakarta Bean Validation 把必填、范围、枚举和嵌套对象校验声明在 DTO 上。
不合规输出会抛 AiStructuredOutputException，并在接口层按模型网关问题处理。
```

## 项目边界讲法

```text
这个项目目前更偏工程化 AI Agent 应用，不是完全自主规划型 Agent。
我采用确定性 Orchestrator 编排，是因为业务系统更关注链路可控、错误边界清楚和可测试。
后续如果引入 Function Calling 或更复杂的 Planner，可以把现有 Tool 继续暴露给模型调用。
```
