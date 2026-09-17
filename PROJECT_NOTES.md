# NutriAI 项目笔记

> 创建日期：2026-09-11
> 当前状态：✅ 核心链路跑通（前端 → Spring Boot → DeepSeek AI → JSON 解析 → 页面渲染）

---

## 项目结构

```
NutriAI/
├── backend/                          # Spring Boot 后端
│   ├── pom.xml                       # Spring Boot 3.2.0 + Java 17
│   ├── src/main/java/com/nutriai/
│   │   ├── NutriAiApplication.java   # 启动类
│   │   ├── config/
│   │   │   ├── NutriAiProperties.java    # 自定义配置属性
│   │   │   └── WebConfig.java            # CORS 跨域
│   │   ├── dto/
│   │   │   ├── UserProfileRequest.java    # 用户输入（性别/年龄/身高/体重/活动/目标/口味/喜好/忌讳）
│   │   │   ├── RecommendationResponse.java # AI 推荐结果（概要/营养/食谱/建议）
│   │   │   └── ApiResponse.java           # 统一响应包装
│   │   ├── ai/                            # ⭐ AI 抽象层（策略模式）
│   │   │   ├── AiService.java              # 接口
│   │   │   ├── AiServiceFactory.java       # 工厂，按配置路由，失败自动降级 mock
│   │   │   ├── MockAiService.java           # Mock 实现（无需 Key 即可跑通）
│   │   │   └── OpenAiCompatAiService.java   # DeepSeek/OpenAI 兼容实现
│   │   ├── service/
│   │   │   └── NutritionService.java        # 业务编排
│   │   ├── controller/
│   │   │   └── NutritionController.java     # REST API
│   │   └── exception/
│   │       ├── AiApiException.java
│   │       └── GlobalExceptionHandler.java
│   └── src/main/resources/
│       └── application.yml                 # 配置
└── frontend/                         # 前端原型
    ├── index.html
    ├── css/style.css
    └── js/app.js
```

## 当前运行环境

| 项目 | 值 |
|------|-----|
| Java | JDK 21.0.9（系统默认是 Java 8，必须切！） |
| Maven | 3.9.9（便携版在 `D:\apache-maven-3.9.9`） |
| 后端端口 | 8080 |
| 前端端口 | 3000（Python http.server） |
| 数据库 | H2 内存库（console: `/h2-console`） |
| AI Provider | openai-compat (DeepSeek) |

## 启动命令（完整）

每次开新终端都要重新设置环境变量，或者配到系统环境变量里：

```powershell
# 1. 后端
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.9'
$env:MAVEN_HOME = 'D:\apache-maven-3.9.9'
$env:PATH = "$env:MAVEN_HOME\bin;$env:JAVA_HOME\bin;" + [Environment]::GetEnvironmentVariable('PATH','Machine') + ';' + [Environment]::GetEnvironmentVariable('PATH','User')
cd d:\Web_JavaWork\NutriAI\backend
mvn spring-boot:run

# 2. 前端（另开一个终端）
cd d:\Web_JavaWork\NutriAI\frontend
python -m http.server 3000
```

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/recommend` | 提交用户信息，AI 返回推荐 |
| GET  | `/api/ai/providers` | 查看已注册 AI 服务商列表 |
| GET  | `/api/health` | 健康检查 |

## AI 配置 (application.yml)

```yaml
nutriai:
  ai:
    provider: openai-compat
    api-key: sk-xxxxx          # DeepSeek Key 在这里
    api-endpoint: https://api.deepseek.com/v1
    model: deepseek-chat
```

**切换 Mock**：把 provider 改成 `mock` 即可，不需要 Key。

## AI 抽象层设计要点（未来扩展）

- 新增 AI 服务商 → 实现 `AiService` 接口 + `@Component`，自动被 `AiServiceFactory` 扫描注册
- 每个 Service 的 `getProviderName()` 必须唯一（之前踩过坑：两个都叫 "mock" 会覆盖）
- `AiServiceFactory.getService()` 会根据配置路由，目标不可用时自动降级 mock

## 遇到的坑 & 修复

| 问题 | 原因 | 修复 |
|------|------|------|
| Maven 默认跑 Java 8，Spring Boot 3.x 编译失败 | `JAVA_HOME` 指向 jdk1.8 | 手动设 `JAVA_HOME=C:\Program Files\Java\jdk-21.0.9` |
| OpenAiCompatAiService 的 `getProviderName()` 返回配置值，和 MockAiService 重名 | 两个都注册成 "mock" 后者覆盖前者 | OpenAiCompatAiService 固定返回 `"openai-compat"` |
| MockAiService 编译报 `NutritionalInfo` 找不到 | 写了 `RecipeDto.NutritionalInfo` 但正确路径是 `RecommendationResponse.NutritionalInfo` | 修正嵌套类路径 |
| DeepSeek 返回 402 Payment Required | 账户未充值 | 充值后正常（1元即可） |
| AI 返回内容带 ```json 代码块包裹，直接反序列化失败 | LLM 经常这样输出 | `parseResponse` 里先用正则提取 JSON 再反序列化 |

## 下一步可以做的事情

- [ ] Prompt 工程优化（让 AI 输出更精准、更有差异化）
- [ ] 用户历史记录存储（建 JPA Entity + Repository）
- [ ] 食谱数据库（预置一批本地食谱作为 fallback）
- [ ] AI 流式响应（WebFlux + SSE 打字机效果）
- [ ] 更多 AI Provider（如通义千问、本地 Ollama）
- [ ] 前端升级到 Vue/React
- [ ] 用户登录 / 体重追踪图表
- [ ] 单元测试 + 集成测试

## 关键文件速查

| 你想... | 去这个文件 |
|---------|-----------|
| 改 AI Prompt | `OpenAiCompatAiService.java` 的 `buildPrompt()` |
| 改 AI 返回的 JSON 解析 | `OpenAiCompatAiService.java` 的 `parseResponse()` / `extractJson()` |
| 改 Mock 生成的食谱/营养计算 | `MockAiService.java` |
| 改 DTO 字段 | `dto/UserProfileRequest.java` 或 `dto/RecommendationResponse.java` |
| 改配置（API Key/模型/端口） | `application.yml` |
| 改前端样式/交互 | `frontend/index.html` + `css/style.css` + `js/app.js` |