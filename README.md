# AI智能面试辅助系统

一个基于大语言模型的智能面试辅助系统，提供简历智能解析、多维度评分、个性化面试问题生成与答案评估功能。

## 功能特点

### 1. 智能简历评分
- 多维度深度分析（项目经验、技能匹配、内容完整性、结构清晰度、表达专业性）
- 提供具体的优化建议和改进方案
- 基于资深技术架构师视角进行深度审计

### 2. 个性化模拟面试
- 根据简历内容定制专属面试问题
- 覆盖 Java 基础、并发编程、数据库、缓存、Spring、AI 框架等多个技术领域
- 题目难度梯度分布（基础 30%、进阶 50%、专家 20%）

### 3. 深度答案评估
- 全方位专业评估（准确性 40%、完整性 20%、深度 25%、表达 15%）
- 详细反馈指出优点与不足
- 提供源码级参考答案和核心要点

## 技术架构

- **后端框架**: Spring Boot 3.5.7
- **AI 框架**: Spring AI Alibaba (通义千问)
- **数据持久化**: MySQL + MyBatis-Plus
- **缓存层**: Redis
- **模板引擎**: Thymeleaf
- **开发语言**: Java 17
- **前端**: HTML5 + CSS3 + JavaScript

## 快速开始

### 环境要求
- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- 通义千问 API Key

### 数据库初始化

```sql
CREATE DATABASE ai_interview CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

表结构由 MyBatis-Plus 自动创建（需配置 `spring.sql.init.mode=always` 或手动执行建表SQL）。

### 配置步骤

1. **设置环境变量**
   ```bash
   export DASHSCOPE_API_KEY=your-api-key-here
   export MYSQL_HOST=localhost
   export MYSQL_PORT=3306
   export MYSQL_DB=ai_interview
   export MYSQL_USER=root
   export MYSQL_PASSWORD=root
   export REDIS_HOST=localhost
   export REDIS_PORT=6379
   ```

2. **启动应用**
   ```bash
   mvn spring-boot:run
   ```

3. **访问应用**
   打开浏览器访问：http://localhost:8080

## 核心架构设计

### 存储层设计
- **MySQL**: 简历元数据、评分结果、面试问题、评估结果持久化存储
- **Redis**: 热点数据缓存（简历评分结果、面试会话状态），TTL 1小时
- **缓存策略**: 读时先查Redis，未命中再查MySQL，回写缓存

### AI 集成
- **Prompt 工程**: System/User Prompt 分离，4套结构化 Prompt 模板
- **模型调用**: Spring AI Alibaba DashScopeChatModel，temperature 0.7
- **输出治理**: JSON 结构化输出 + Markdown 代码块清洗 + 字段级容错解析

### 项目结构
```
ai-interview-assistant/
├── src/main/
│   ├── java/com/xkh/ai/interview/
│   │   ├── config/              # 配置类（MyBatis-Plus、Redis）
│   │   ├── controller/          # Web 控制器
│   │   ├── entity/              # 数据库实体
│   │   ├── mapper/              # MyBatis-Plus Mapper
│   │   ├── service/             # 业务逻辑 + DTO
│   │   └── AiInterviewAssistantApplication.java
│   └── resources/
│       ├── mapper/              # MyBatis XML（预留）
│       ├── prompt/              # AI 提示词模板
│       ├── templates/           # HTML 页面
│       └── application.yml      # 配置文件
└── pom.xml
```

## 许可证

Apache License 2.0
