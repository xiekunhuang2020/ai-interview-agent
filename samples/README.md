# Demo 样例数据

这些文件用于固定演示流程，避免每次临时手写简历、岗位说明和面试答案。

## 简历样例

- `java-backend-resume.txt`：Java 后端通用简历。
- `ai-application-resume.txt`：AI 应用开发方向简历。
- `platform-backend-resume.txt`：平台后端和运营看板方向简历。

## 岗位样例

- `java-ai-agent-jd.txt`：Java AI Agent 应用开发岗位。
- `java-backend-performance-jd.txt`：Java 后端性能优化岗位。
- `fullstack-ai-product-jd.txt`：Java 全栈 + AI 应用岗位。

## 答案样例

- `interview-answers-demo.json`：提交面试答案接口可直接使用的固定答案。

## 评测样例

- `eval/rag-recall-cases.json`：RAG TopK 召回评估样例。
- `eval/prompt-rag-evaluation-cases.json`：Prompt/RAG 评测回放样例，覆盖简历分析、岗位匹配和岗位定制出题。

## 推荐演示命令

```powershell
powershell -ExecutionPolicy Bypass -File scripts/demo-flow.ps1
```

切换样例：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/demo-flow.ps1 `
  -ResumePath samples/ai-application-resume.txt `
  -JobDescriptionPath samples/fullstack-ai-product-jd.txt
```
