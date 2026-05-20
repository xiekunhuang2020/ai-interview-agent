# 展示前检查清单

## GitHub 前置检查

- README 能在 1 分钟内说明项目定位、技术栈和亮点。
- `docs/architecture.md` 能讲清 Page Controller、API Controller、Workflow Service、Agent 和 Tool。
- `docs/api-examples.md` 覆盖上传简历、基础出题、岗位匹配、根据岗位生成面试题、评估、审计和看板接口。
- `.env.example` 不包含真实 API Key 或密码。
- `samples/` 中有展示用简历和 JD。
- `docker-compose.yml` 覆盖 MySQL、Redis、Milvus 依赖。
- `sql/init.sql` 包含所有表结构。
- 旧库迁移脚本 `sql/migration-v2-ai-model-call-log.sql` 和 `sql/migration-v3-agent-conversation-message.sql` 已保留。

## 运行前检查

```bash
mvn -q -DskipTests compile
```

本机装 Docker 时：

```bash
docker compose up -d
docker compose ps
curl http://localhost:9091/healthz
mvn spring-boot:run
```

## 展示路径

```text
1. 打开 http://localhost:8080
2. 上传 samples/java-backend-resume.txt
3. 查看简历分析报告
4. 调用 /api/jd/{resumeId}/match 查看岗位匹配
5. 调用 /api/interview/{resumeId}/rag-questions 根据岗位生成面试题
6. 提交答案并查看评估报告
7. 打开 /audit/prompt-dashboard 查看 Prompt 效果看板
```

## 面试前准备

- 能用 30 秒讲清项目定位。
- 能用 2 分钟讲清业务流程。
- 能画出 Page Controller、API Controller、Workflow Service、Agent 和 Tool。
- 能解释为什么采用确定性 Workflow Service，而不是完全自主 Agent。
- 能解释 RAG 检索上下文如何防止张冠李戴。
- 能解释 Spring AI 模型重试、traceId、异常映射和审计怎么协作。
- 能解释 BeanOutputConverter 和 Bean Validation 如何治理结构化输出。
- 能诚实说明这是个人自研项目，没有虚构线上 QPS 或准确率。

## 面试时优先展示的文件

```text
README.md
docs/architecture.md
src/main/java/com/xkh/ai/interview/service/workflow/InterviewWorkflowService.java
src/main/java/com/xkh/ai/interview/service/llm/AiModelCallService.java
src/main/java/com/xkh/ai/interview/service/llm/AiJsonResponseParser.java
src/main/resources/templates/prompt-dashboard.html
docs/career-package/technical-faq.md
```

## 简历投递检查

- 项目名称不要写得像公司项目。
- 不写“精通 AI Agent”。
- 不写没有数据支撑的 QPS、准确率、用户规模。
- 强调“工程化 AI 应用落地能力”。
- 强调 Java 后端经验向 AI Agent 应用开发迁移。
