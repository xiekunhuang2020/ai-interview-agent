# API 示例

## 上传简历并生成分析

```bash
curl -X POST http://localhost:8080/api/resume/upload \
  -H "X-Trace-Id: trace-resume-upload-001" \
  -F "file=@samples/java-backend-resume.txt"
```

响应：

```json
{
  "resumeId": "uuid",
  "scoreResult": {
    "overallScore": 82
  }
}
```

所有接口都会在响应头返回 `X-Trace-Id`。如果请求未传入该 header，服务端会自动生成，方便把 HTTP 请求、工作流编排和模型调用日志串起来。

## 获取简历工作台数据

```bash
curl http://localhost:8080/api/resume/{resumeId}
```

响应会包含 `session` 字段，用于展示当前简历所在流程阶段，例如 `ANALYZED`、`JD_MATCHED`、`QUESTIONS_GENERATED`、`ANSWER_SUBMITTED`、`EVALUATED` 或 `FAILED`。

## 生成面试问题

```bash
curl -X POST http://localhost:8080/api/interview/{resumeId}/questions
```

## 提交答案并生成评估

```bash
curl -X POST http://localhost:8080/api/interview/{resumeId}/submit \
  -H "Content-Type: application/json" \
  -d "{\"0\":\"我会从业务场景、技术方案、结果指标三个层次回答。\"}"
```

## 岗位匹配分析

```bash
curl -X POST http://localhost:8080/api/jd/{resumeId}/match \
  -H "Content-Type: application/json" \
  -d "{\"jobDescription\":\"Java AI 应用开发工程师，要求 Spring Boot、Spring AI、RAG、Milvus、Redis、工程化经验\"}"
```

响应会包含总匹配分、匹配等级、已匹配技能、缺失技能、风险点和学习建议。

## 根据岗位生成面试题

```bash
curl -X POST http://localhost:8080/api/interview/{resumeId}/rag-questions \
  -H "Content-Type: application/json" \
  -d "{\"jobDescription\":\"Java AI 应用开发工程师，要求 Spring Boot、Spring AI、RAG、Milvus、Redis、工程化经验\", \"topK\": 5}"
```

该接口对应页面里的“根据岗位生成面试题”。服务端会先用目标岗位说明检索向量库中的相似简历片段，再结合候选人简历生成岗位定制化面试题，并保存到当前面试会话。这里的 RAG 是技术实现方式，用户侧不需要理解“增强题”这类技术词。

## 查询模型调用审计

```bash
curl "http://localhost:8080/api/audit/model-calls?traceId=trace-resume-upload-001&limit=20"
```

也可以按 operation 查询：

```bash
curl "http://localhost:8080/api/audit/model-calls?operationName=jd-match&limit=20"
```

## 查询 Prompt 指标

```bash
curl "http://localhost:8080/api/audit/prompt-metrics?operationName=jd-match&limit=1000"
```

响应会按 `operationName + promptVersion` 聚合模型调用总数、成功率、失败数、平均耗时和最大耗时。`avgAttemptCount` 保留用于兼容早期外层重试审计；当前模型侧重试由 Spring AI 管理。

## 查询失败原因分布

```bash
curl "http://localhost:8080/api/audit/failure-reasons?operationName=jd-match&limit=1000"
```

## Prompt 看板页面

```text
http://localhost:8080/audit/prompt-dashboard
```

## 旧库迁移

如果本地数据库是在审计能力补齐前创建的，需要按顺序手动执行：

```bash
mysql -uroot -p ai_interview < sql/migration-v2-ai-model-call-log.sql
mysql -uroot -p ai_interview < sql/migration-v3-agent-conversation-message.sql
mysql -uroot -p ai_interview < sql/migration-v4-interview-session.sql
```
