# NutriAI 项目笔记

> 创建日期：2026-09-11
> 最后更新：2026-09-23
> 当前状态：✅ 第一周 AI 推荐 + 第二周 用户登录/画像/历史记录，全部跑通

---

## 快速启动

```
双击 start.bat  →  自动打包 + 启动后端 + 浏览器打开 http://localhost:8080
```

## 项目结构

```
NutriAI/
├── start.bat                        ⭐ 一键启动脚本
├── PROJECT_NOTES.md                 本文档
├── NutriAI_Week1_Report.pptx        第一周汇报 PPT
├── NutriAI_Week2_Report.md          第二周汇报（Markdown，内容更简洁）
├── backend/                          Spring Boot 后端
│   ├── pom.xml                       Spring Boot 3.2.0 + Java 21
│   ├── src/main/java/com/nutriai/
│   │   ├── NutriAiApplication.java   启动类
│   │   ├── config/
│   │   │   ├── SecurityConfig.java      ⭐ NEW - Spring Security + Session
│   │   │   ├── WebConfig.java           CORS 跨域
│   │   │   └── NutriAiProperties.java   自定义配置属性
│   │   ├── entity/                      ⭐ NEW - JPA 实体
│   │   │   ├── User.java               用户表
│   │   │   ├── UserProfile.java         用户画像表
│   │   │   └── RecommendationRecord.java 推荐历史表
│   │   ├── repository/                  ⭐ NEW - Spring Data JPA
│   │   │   ├── UserRepository.java
│   │   │   ├── UserProfileRepository.java
│   │   │   └── RecommendationRecordRepository.java
│   │   ├── dto/
│   │   │   ├── ApiResponse.java         统一响应包装
│   │   │   ├── UserProfileRequest.java  用户输入
│   │   │   ├── RecommendationResponse.java AI 推荐结果
│   │   │   ├── AuthRequest.java         ⭐ 登录/注册请求
│   │   │   ├── AuthResponse.java        ⭐ 登录/注册响应
│   │   │   ├── UserSummaryDto.java      ⭐ 当前用户信息
│   │   │   ├── UserProfileDto.java      ⭐ 画像数据
│   │   │   ├── HistoryRecordDto.java    ⭐ 历史列表项
│   │   │   └── HistoryDetailDto.java    ⭐ 历史详情
│   │   ├── ai/                          AI 抽象层（策略模式）
│   │   │   ├── AiService.java
│   │   │   ├── AiServiceFactory.java    工厂 + 自动降级 mock
│   │   │   ├── MockAiService.java
│   │   │   └── OpenAiCompatAiService.java
│   │   ├── service/                     业务逻辑
│   │   │   ├── NutritionService.java
│   │   │   ├── AuthService.java          ⭐ 注册/登录/登出
│   │   │   ├── UserProfileService.java   ⭐ 画像保存/读取
│   │   │   ├── HistoryService.java       ⭐ 历史记录管理
│   │   │   └── CustomUserDetailsService.java ⭐ Security 用户加载
│   │   ├── controller/                  REST API
│   │   │   ├── NutritionController.java
│   │   │   ├── AuthController.java       ⭐ /api/auth/*
│   │   │   ├── UserProfileController.java ⭐ /api/user/profile
│   │   │   └── HistoryController.java    ⭐ /api/recommend/history
│   │   └── exception/
│   │       ├── AiApiException.java
│   │       └── GlobalExceptionHandler.java
│   └── src/main/resources/
│       ├── application.yml              配置
│       └── static/                      ⭐ 前端文件（同步自 frontend/）
│           ├── index.html
│           ├── css/style.css
│           └── js/app.js
└── frontend/                           前端开发目录
    ├── index.html
    ├── css/style.css
    └── js/app.js
```

## 技术栈总览

| 层 | 技术 | 版本 |
|----|------|------|
| 后端框架 | Spring Boot | 3.2.0 |
| 编程语言 | Java | 21（⚠️ 系统默认是 Java 8，必须用 JDK 21） |
| 安全认证 | Spring Security + BCrypt | Session Cookie 模式 |
| 数据库 | H2（内存） | 开发用，易重启 |
| ORM | Spring Data JPA + Hibernate | DDL 自动更新 |
| AI 接口 | DeepSeek（OpenAI 兼容）| deepseek-chat |
| 前端 | 原生 HTML + CSS + JS | 无构建步骤 |
| 构建 | Maven | 3.9.9 |

## API 接口清单

### 公开接口（游客可用）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 前端页面 |
| POST | `/api/auth/register` | 注册新用户 |
| POST | `/api/auth/login` | 登录（返回 Session Cookie） |
| GET | `/api/auth/me` | 检查当前登录状态 |
| POST | `/api/recommend` | 生成 AI 推荐（游客也能用） |
| GET | `/api/health` | 健康检查 |

### 需登录接口（Session Cookie 验证）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/user/profile` | 获取已保存的用户画像 |
| PUT | `/api/user/profile` | 保存/更新画像 |
| DELETE | `/api/user/profile` | 删除画像 |
| GET | `/api/recommend/history` | 历史推荐列表（分页） |
| GET | `/api/recommend/history/{id}` | 历史详情 |
| DELETE | `/api/recommend/history/{id}` | 删除一条历史 |
| POST | `/api/auth/logout` | 登出 |

## 认证流程

```
浏览器                    后端 (Spring Security)
  │                         │
  │── POST /api/auth/login ─▶  AuthService.login()
  │   {username, password}  │   → AuthenticationManager 验证
  │                         │   → SecurityContext 存入 Session
  │◀── {Set-Cookie: JSESSIONID=xxx} │
  │                         │
  │── GET /api/user/profile ─▶  SecurityFilterChain 检查 Cookie
  │   Cookie: JSESSIONID=xxx │   → 找到 Session → 已认证
  │◀── 200 OK + 数据        │
  │                         │
  │── POST /api/auth/logout ─▶  SecurityContextLogoutHandler
  │◀── 200 OK + 清除 Cookie │
```

## 数据库表结构（Hibernate 自动创建）

### users
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 用户 ID |
| username | VARCHAR(50) UNIQUE | 用户名 |
| email | VARCHAR(100) UNIQUE | 邮箱 |
| password | VARCHAR(200) | BCrypt 加密后的密码 |
| display_name | VARCHAR(50) | 显示名 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

### user_profiles
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 画像 ID |
| user_id | BIGINT FK | 关联 users.id |
| gender | VARCHAR | MALE/FEMALE/OTHER |
| age | INT | 年龄 |
| height_cm | DOUBLE | 身高 |
| weight_kg | DOUBLE | 体重 |
| activity_level | VARCHAR | 活动水平 |
| health_goal | VARCHAR | 健康目标 |
| taste_preference | VARCHAR | 口味偏好 |
| food_preferences_json | TEXT | 食物喜好（JSON 数组） |
| food_allergies_json | TEXT | 食物忌讳（JSON 数组） |
| created_at / updated_at | TIMESTAMP | 时间戳 |

### recommendation_records
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 记录 ID |
| user_id | BIGINT FK | 关联 users.id |
| profile_snapshot | TEXT | 推荐时的用户画像快照（JSON） |
| recommendation_json | TEXT | AI 推荐结果（JSON） |
| ai_provider | VARCHAR | 使用的 AI 服务商 |
| created_at | TIMESTAMP | 创建时间 |

## 遇到的坑 & 修复

### 第二周（用户系统）

| # | 问题 | 原因 | 修复 |
|---|------|------|------|
| 1 | 点击"注册"按钮没反应 | Spring Security 的 `authorizeHttpRequests` 只放行了 `/api/**`，静态资源 `/`, `/index.html`, `/js/**`, `/css/**` 全部被拦截返回 500 | 在 SecurityConfig 里添加静态资源路径到 `permitAll()` |
| 2 | 前端登录 Modal 不显示 | 新版前端文件（有 Modal）放在 `frontend/` 目录，但后端 `resources/static/` 里还是**旧版**（没 Modal），后端直接提供旧版静态资源 | 每次前端改完，把 `frontend/` 三个文件复制到 `backend/resources/static/` |
| 3 | 注册接口返回 500 | PowerShell 的 curl.exe 引号转义问题（`-d '{"username":"x"}'` 在 PowerShell 里会变成无效 JSON） | 用 `Invoke-RestMethod -Body $body` 或 `curl.exe --%` |
| 4 | Session Cookie 不创建 | `SessionCreationPolicy` 没设为 `ALWAYS`，匿名请求不会建 Session | 改成 `SessionCreationPolicy.ALWAYS` |
| 5 | 登录后 `SecurityContext` 不持久化到 Session | 虽然 Security 框架会自动做，但某些情况下需要显式保存 | 注入 `SecurityContextRepository`，在 AuthController.login() 里手动 `saveContext()` |
| 6 | 直接启动 jar 失败 "没有主清单属性" | 手动用 `jar cf` 重新打包时丢了 Spring Boot 的 manifest 和 BOOT-INF/lib | 一定要用 `mvn package`，不要手动 jar 打包 fat jar |

### 第一周（AI 推荐核心）

| # | 问题 | 原因 | 修复 |
|---|------|------|------|
| 7 | Maven 默认跑 Java 8，Spring Boot 3.x 编译失败 | JAVA_HOME 指向 jdk1.8 | 手动设 `JAVA_HOME=C:\Program Files\Java\jdk-21.0.9` |
| 8 | OpenAiCompatAiService 和 MockAiService 的 getProviderName() 重名 | 两个都注册成 "mock"，后者覆盖前者 | OpenAiCompatAiService 固定返回 `"openai-compat"` |
| 9 | AI 返回内容带 ```json 代码块包裹，反序列化失败 | LLM 经常这样输出 | `extractJson()` 里先用正则 `\{[\s\S]*\}` 提取 JSON 再反序列化 |
| 10 | DeepSeek 返回 402 Payment Required | 账户未充值 | 充值后正常 |

## 前端修改工作流（重要！）

```
改 frontend/index.html 或 frontend/js/app.js 或 frontend/css/style.css
         │
         ▼
   复制到 backend/src/main/resources/static/
   （start.bat 里会自动做这一步）
         │
         ▼
   mvn package → 后端 jar 里包含最新前端
         │
         ▼
   访问 http://localhost:8080/
```

**如果只改了 `frontend/` 而没同步到 `static/`，后端会继续提供旧版页面！**

## 关键文件速查

| 你想... | 去这个文件 |
|---------|-----------|
| 改注册/登录逻辑 | `service/AuthService.java` + `controller/AuthController.java` |
| 改 Security 放行规则 | `config/SecurityConfig.java` 的 `authorizeHttpRequests()` |
| 改 AI Prompt | `ai/OpenAiCompatAiService.java` 的 `buildPrompt()` |
| 改 Mock 数据 | `ai/MockAiService.java` |
| 改 API Key / 模型 | `src/main/resources/application.yml` |
| 改前端样式 | `frontend/css/style.css` |
| 改前端交互 | `frontend/js/app.js` |
| 改数据库表结构 | `entity/*.java`（改完后 JPA 会自动 DDL update） |

## 环境信息速查

| 项目 | 值 |
|------|-----|
| JDK 21 | `C:\Program Files\Java\jdk-21.0.9` |
| Maven | `D:\apache-maven-3.9.9` |
| 后端端口 | 8080 |
| 访问地址 | http://localhost:8080 |
| 数据库 | H2 内存库（重启会清空） |
| AI Provider | openai-compat (DeepSeek) |
| AI 切换 Mock | `application.yml` → `provider: mock` |

## 下一步可以做

- [ ] H2 换成 MySQL 持久化数据
- [ ] 密码找回 / 邮箱验证
- [ ] 体重追踪图表（前端 Chart.js）
- [ ] AI 流式响应（打字机效果）
- [ ] Prompt 工程优化
- [ ] 单元测试 + 集成测试
- [ ] Docker 部署