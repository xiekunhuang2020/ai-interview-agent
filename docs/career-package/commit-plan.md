# 提交拆分建议

如果你准备把项目推到 GitHub，建议按能力分组拆 commit。这样提交历史更像真实项目演进，也方便面试时讲清楚每一轮改造。

## 推荐提交顺序

```text
feat(agent): introduce orchestrator, agents and tools
feat(rag): add job matching and RAG-based tailored questions
feat(model): use ChatClient with Spring AI retry and audit mapping
feat(audit): add prompt versioning, model call logs and agent conversation audit
feat(dashboard): add prompt metrics dashboard
docs(project): add architecture, API examples and runtime docs
docs(career): add resume and interview preparation package
```

## 每个提交包含什么

### feat(agent)

```text
src/main/java/.../agent/
src/main/java/.../orchestrator/
src/main/java/.../tool/
src/main/java/.../support/AiJsonResponseParser.java
```

说明重点：从单 Service 调 LLM 改造成 Controller + Orchestrator + Agent + Tool。

### feat(rag)

```text
JobDescriptionMatchAgent
RagInterviewQuestionAgent
JobDescriptionMatchResult
JobDescriptionRequest
jd-match-system.st
rag-interview-question-system.st
samples/java-ai-agent-jd.txt
```

说明重点：支持岗位匹配和岗位定制出题。

### feat(model)

```text
AiModelCallService
AiModelCallException
AiStructuredOutputException
RequestTraceFilter
application.yml 中 spring.ai.retry 配置
```

说明重点：Spring AI 框架级重试、Prompt 版本、调用审计、异常映射和 traceId。

### feat(audit)

```text
AiModelCallLog
AiModelCallLogMapper
AiModelCallAuditRecorder
AgentConversationMessage
AgentConversationMessageMapper
AgentConversationAuditRecorder
AgentConversationAuditQueryService
PromptVersionRegistry
sql/init.sql
sql/migration-v2-ai-model-call-log.sql
sql/migration-v3-agent-conversation-message.sql
```

说明重点：Prompt 版本、模型调用审计、Agent 对话审计和旧库迁移脚本。

### feat(dashboard)

```text
PromptMetricsResult
PromptFailureReasonResult
AiModelCallAuditQueryService
prompt-dashboard.html
InterviewController 中 /audit/prompt-dashboard 和 /api/audit/* 接口
```

说明重点：Prompt 效果指标和失败原因看板。

### docs(project)

```text
README.md
docs/architecture.md
docs/api-examples.md
.env.example
docker-compose.yml
samples/java-backend-resume.txt
```

说明重点：项目可运行、可展示、可理解。

### docs(career)

```text
docs/career-package/
```

说明重点：简历材料、面试讲稿和展示清单。

## 提交前检查命令

```bash
mvn -q -DskipTests compile
git status --short
git diff --stat
```

## 注意

如果当前工作区已经有 staged changes，先用下面命令确认：

```bash
git status --short
```

不要用 `git reset --hard` 清理工作区，避免误删已有改造结果。
