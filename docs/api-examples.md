# API Examples

## 上传简历并生成分析

```bash
curl -X POST http://localhost:8080/api/resume/upload \
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

