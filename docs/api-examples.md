# API 示例

## 一键演示脚本

脚本会自动完成上传简历、岗位匹配、生成岗位定制题、提交固定答案和 RAG 召回评估。

```powershell
cd C:\code\AIStudy\ai-interview-agent
powershell -ExecutionPolicy Bypass -File scripts/demo-flow.ps1
```

切换演示样例：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/demo-flow.ps1 `
  -ResumePath samples/platform-backend-resume.txt `
  -JobDescriptionPath samples/java-backend-performance-jd.txt
```

## 上传简历并生成分析

Windows PowerShell 里请使用 `curl.exe`，不要直接写 `curl`，因为 `curl` 可能会被解析成 `Invoke-WebRequest`。下面命令需要在项目根目录执行。

```bash
cd C:\code\AIStudy\ai-interview-agent
curl.exe -X POST http://localhost:8080/api/resume/upload -H "X-Trace-Id: trace-resume-upload-001" -F "file=@samples/java-backend-resume.txt"
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

该接口对应页面里的“根据岗位生成面试题”。服务端会先用目标岗位说明检索向量库中的相似简历片段，再结合候选人简历生成岗位定制化面试题，并保存到当前面试会话。这里的 RAG 是技术实现方式，用户侧不需要理解底层检索细节。

## 运行 RAG 召回评估集

```bash
curl "http://localhost:8080/api/evaluation/rag-recall?topK=5"
```

运行前先上传 `samples/java-backend-resume.txt`，让 Milvus 中有可召回的样例数据。响应会包含 TopK 命中率、平均召回耗时、每条样例命中的关键词和未命中样例列表。

## 运行 Prompt/RAG 评测回放

推荐在运营看板点击“运行评测”查看结果：

```text
http://localhost:8080/audit/prompt-dashboard
```

后端接口：

```bash
curl.exe -X POST "http://localhost:8080/api/evaluation/prompt-rag?topK=5"
```

接口会使用 `samples/eval/prompt-rag-evaluation-cases.json` 中的固定样例，回放简历分析、JD 匹配和 RAG 出题，并通过 Spring AI 官方 Evaluator 返回相关性、事实一致性、失败样例和耗时。

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

响应会按 `operationName + promptVersion` 聚合模型调用总数、模型名称、成功率、失败数、平均耗时、输入 Token、输出 Token、总 Token、平均 Token、平均 Prompt 字符数和上下文裁剪次数。上下文预算使用 Spring AI 官方 `TokenCountEstimator` 估算 Token，上下文裁剪状态也会在最近模型调用列表中展示。`avgAttemptCount` 保留用于兼容早期外层重试审计；当前模型侧重试由 Spring AI 管理。

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
mysql --default-character-set=utf8mb4 -uroot -p ai_interview < sql/migration-v2-ai-model-call-log.sql
mysql --default-character-set=utf8mb4 -uroot -p ai_interview < sql/migration-v3-agent-conversation-message.sql
mysql --default-character-set=utf8mb4 -uroot -p ai_interview < sql/migration-v4-interview-session.sql
mysql --default-character-set=utf8mb4 -uroot -p ai_interview < sql/migration-v5-core-result-tables.sql
mysql --default-character-set=utf8mb4 -uroot -p ai_interview < sql/migration-v6-clean-resume-info-json-columns.sql
mysql --default-character-set=utf8mb4 -uroot -p ai_interview < sql/migration-v7-question-source.sql
mysql --default-character-set=utf8mb4 -uroot -p ai_interview < sql/migration-v8-ai-model-error-type.sql
mysql --default-character-set=utf8mb4 -uroot -p ai_interview < sql/migration-v9-ai-model-token-usage.sql
mysql --default-character-set=utf8mb4 -uroot -p ai_interview < sql/migration-v10-context-budget-metrics.sql
```
