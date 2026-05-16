# API Examples

## 上传简历并生成分析

```bash
curl -X POST http://localhost:8080/api/resume/upload \
  -H "X-Trace-Id: demo-trace-001" \
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

所有接口都会在响应头返回 `X-Trace-Id`。如果请求未传入该 header，服务端会自动生成，方便把 HTTP 请求、Agent 编排和模型调用日志串起来。

## 获取简历分析

```bash
curl http://localhost:8080/api/resume/{resumeId}/analysis
```

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

## 简历向量检索

```bash
curl -X POST http://localhost:8080/api/rag/search \
  -H "Content-Type: application/json" \
  -d "{\"query\":\"Java 后端，熟悉 Redis 和 Spring Boot，有高并发项目经验\", \"topK\": 5}"
```

## JD 匹配分析

```bash
curl -X POST http://localhost:8080/api/jd/{resumeId}/match \
  -H "Content-Type: application/json" \
  -d "{\"jobDescription\":\"Java AI Agent 应用开发工程师，要求 Spring Boot、Spring AI、RAG、Milvus、Redis、工程化经验\"}"
```

响应会包含总匹配分、匹配等级、已匹配技能、缺失技能、风险点和学习建议。

## RAG 增强岗位定制出题

```bash
curl -X POST http://localhost:8080/api/interview/{resumeId}/rag-questions \
  -H "Content-Type: application/json" \
  -d "{\"jobDescription\":\"Java AI Agent 应用开发工程师，要求 Spring Boot、Spring AI、RAG、Milvus、Redis、工程化经验\", \"topK\": 5}"
```

该接口会先用 JD 检索向量库中的相似简历片段，再结合候选人简历生成岗位定制化面试题，并保存到当前面试会话。
