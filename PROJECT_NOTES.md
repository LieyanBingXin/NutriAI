# NutriAI 项目笔记

> 创建日期：2026-09-11
> 最后更新：2026-11-18（第九周：视觉改版 + 移动端/PWA + Cloudflare Tunnel 公网上线，固定地址 https://nutri-ai.top）
> 当前状态：✅ Java 21 + Spring Boot 3.2 + MySQL 8；68 个测试全部通过；已通过 Cloudflare Tunnel 实现公网访问（手动开关，不开机自启）
> 第九周 PPT 草稿见 [docs/reports/NutriAI_Week9_PPT_Draft.md](docs/reports/NutriAI_Week9_PPT_Draft.md)，改版明细见 [docs/UI_REDESIGN_2026.md](docs/UI_REDESIGN_2026.md)，部署见 [deploy/README.md](deploy/README.md)

---

## 快速启动

```
1. 双击 start.bat（自动打包 + 启动 + 开浏览器）—— 本地开发 / 测试
2. 浏览器访问 http://localhost:8080
3. 对外访问（内网穿透）：双击 serve-start.bat → 公网 https://nutri-ai.top
   停止：serve-stop.bat
```

## Cloudflare Tunnel 内网穿透上线（2026-11-18）

> 目标：用自机 + 内网穿透实现对外固定地址访问，不租云服务器，不开机自启，手动开关。

### 方案与链路

- Cloudflare Tunnel（免费）：无需公网 IP、自带 HTTPS，走 Cloudflare 境外节点（延迟偏高但带宽充足）
- 域名：`nutri-ai.top`（国内注册商购买，NS 已托管到 Cloudflare）
- 隧道：Cloudflare Dashboard 创建，Tunnel ID `58156186-bce6-4477-ac17-41ace0909a49`
- 配置：`C:\Users\Lenovo\.cloudflared\config.yml`，路由 `nutri-ai.top → http://localhost:8080`
- 客户端：`D:\cloudflared\cloudflared.exe`（版本 2026.11.1）

链路：`https://nutri-ai.top → Cloudflare 节点 → cloudflared 隧道 → localhost:8080 (Spring Boot)`

### 脚本（手动开关，不做开机自启）

| 脚本 | 用途 |
|------|------|
| `serve-start.bat` | 对外服务启动：起后端(8080) + 起 tunnel，输出公网地址 |
| `serve-stop.bat` | 对外服务停止：停 tunnel + 停后端 |
| `start.bat` / `stop.bat` | 本地开发 / 测试（保留不变，用于本地更新） |

### serve-start.bat 要点

- 前置检查：Java 21、jar 是否存在、MySQL 3306 是否监听
- 代理自适应：自动探测 socks5 代理端口 `65532`；开着代理则 tunnel 走代理，没开则直连
- 后端：`java -jar backend\target\nutri-ai-0.1.0.jar --spring.profiles.active=mysql --server.port=8080`
- Tunnel：`cloudflared tunnel run nutriai`

### 验证结果（2026-11-18）

- 本地 `http://localhost:8080` 健康检查 → 200
- 公网 `https://nutri-ai.top` → 200
- MySQL 3306、后端、tunnel 三者均正常运行

### 注意事项

- 电脑重启后不会自动启动，需手动双击 `serve-start.bat`
- 代理关闭时 tunnel 直连（走 Cloudflare 境外节点，速度一般）
- `SESSION_COOKIE_SECURE` 默认 false；HTTPS 环境建议设 true 以让 Cookie 只在 HTTPS 下发送
- 排查时注意：非管理员 PowerShell 的 `Get-NetTCPConnection` 看不到系统服务进程的连接，用 `netstat -ano` 更可靠

---

## 第九周 视觉改版 · 移动端适配 · PWA（2026-11-18）

> 主线：从「课程作业界面」升级为「可上线的健康产品」，补齐视觉、移动端、PWA、部署四个短板。
> 详细改版记录见 [docs/UI_REDESIGN_2026.md](docs/UI_REDESIGN_2026.md)，PPT 草稿见 [docs/reports/NutriAI_Week9_PPT_Draft.md](docs/reports/NutriAI_Week9_PPT_Draft.md)。

### 1. 视觉系统：Editorial Wellness

```
旧：绿色渐变 SaaS；新：浅色纸质编辑风（warm paper / forest green / clay accent）。
· 色彩 token：--paper / --surface / --ink / --forest / --sage / --clay，一套变量驱动明暗双主题
· 版式：桌面固定侧边栏 + 页头 + 大留白；卡片细边框、低阴影、圆角 24
· 首屏：双栏 Hero（文案 + 今日营养玻璃卡 + AI Note 便签）
· 图标：导航线性 SVG + 少量 emoji 点缀
```

### 2. 导航重构

```
· 桌面：固定侧边栏（Workspace / Library 分组 + 底部用户区 + Today's Note）
· 移动：底部导航 5 主入口（AI 助手 / 看板 / 体重 / 日记 / 更多）
· 「更多」= 底部抽屉（画像 / 历史 / 收藏 / 食谱本 / 切换主题 / 退出）
· 页头随 Tab 切换标题与日期；PWA 深链 ?tab=assistant|diary|weight
```

### 3. 移动端 + PWA

```
· viewport-fit=cover + env(safe-area-inset-bottom) 适配刘海屏/手势条
· 底部导航固定、触控目标 ≥ 34-40px、去点按高亮与 300ms 延迟
· 输入框 16px 避免 iOS 聚焦缩放；聊天页 100dvh 键盘弹出输入框保持可视
· 移动端隐藏桌面便利贴侧栏，优先对话与记录路径
· PWA：manifest + 192/512/maskable 图标 + Service Worker + 离线页 + 安装提示
· SW 不缓存 /api/（隐私数据不落盘）；静态资源 stale-while-revalidate
· 缓存刷新：静态资源 ?v=xxx 版本号 + SW 缓存版本号双控
```

### 4. 移动端便利贴与输入区细节修复（2026-11-18）

```
① 便利贴抽屉列表横向排列、可左右拖动 → 纵向堆叠、占满宽度、禁换行，上下滑动
② 标签挂载区被输入框遮挡 → .chat-card 补 flex 纵向 + overflow:hidden，
   挂载区 flex-shrink:0 + z-index:3 + max-height + 内部滚动，
   输入框 flex-shrink:0 + z-index:1，层级反转保证挂载区在上
③ 便利贴/会话标签占满首屏 → 移动端隐藏，保留底部「更多」入口
```

### 5. 响应式回归（320–1440px）

```
· 覆盖 320×780 … 2560×1440 及 844×390 横屏共 11 档
· 方法：无头 Edge 检测 scrollWidth > clientWidth / 视口越界 / nowrap 裁切 + 逐张截图
· 结果：全部页面 0 溢出 / 竖排问题
· 大屏：工作区最大 1520px 居中；助手内容列 1280px 居中
```

### 6. 部署与验证

```
· 部署：见上节「Cloudflare Tunnel 内网穿透上线」——固定公网 https://nutri-ai.top，手动开关不开机自启
· mvn clean package BUILD SUCCESS，68 测试全部通过
· 桌面 1440×1000、安卓 412×915 逐页验证通过
· PWA 资源 manifest/sw.js/offline.html/icons/vendor 全部 200
```

---

## 项目结构

```
NutriAI/
├── start.bat                        ⭐ 一键启动脚本（含 AI_API_KEY）
├── PROJECT_NOTES.md                 本文档
├── NutriAI_Week4_Report.pptx        ⭐ NEW 第四周汇报 PPT
├── backend/                          Spring Boot 后端
│   ├── pom.xml                       Spring Boot 3.2.0 + Java 21
│   │                                 ⭐ MySQL Connector + Spring Boot Mail
│   ├── src/main/java/com/nutriai/
│   │   ├── NutriAiApplication.java   启动类
│   │   ├── config/
│   │   │   ├── SecurityConfig.java      Session 认证 + permitAll
│   │   │   ├── WebConfig.java           ⭐ CORS + ObjectMapper + JavaTimeModule（第四周修复）
│   │   │   └── NutriAiProperties.java   自定义配置
│   │   ├── entity/                      JPA 实体
│   │   │   ├── User.java               用户（含邮箱验证/重置密码字段）
│   │   │   ├── UserProfile.java         ⭐ 新增 dailyCalorieGoal/proteinGoal/carbsGoal/fatGoal
│   │   │   ├── FoodDiary.java           食物日记（第四周）
│   │   │   ├── FavoriteRecipe.java      ⭐ NEW 收藏食谱（第五周）
│   │   │   ├── FoodPreference.java      ⭐ NEW 食物偏好 LIKE/DISLIKE（第五周）
│   │   │   ├── RecommendationRecord.java 推荐历史
│   │   │   └── WeightRecord.java        体重追踪
│   │   ├── repository/                  Spring Data JPA
│   │   │   ├── UserRepository.java
│   │   │   ├── UserProfileRepository.java
│   │   │   ├── FoodDiaryRepository.java
│   │   │   ├── FavoriteRecipeRepository.java  ⭐ NEW（第五周）
│   │   │   ├── FoodPreferenceRepository.java  ⭐ NEW（第五周）
│   │   │   ├── RecommendationRecordRepository.java
│   │   │   └── WeightRecordRepository.java
│   │   ├── dto/
│   │   │   ├── ApiResponse.java         统一响应包装
│   │   │   ├── FoodDiaryRequest.java    食物日记请求（含 @JsonFormat）
│   │   │   ├── FoodDiaryDto.java        食物日记响应
│   │   │   ├── DailyNutritionDto.java   每日营养汇总（含目标+达标率）
│   │   │   ├── FoodRecognitionResult.java ⭐ NEW AI识别食物返回（第五周）
│   │   │   ├── UserProfileDto.java      新增 4 个目标字段
│   │   │   ├── RecommendationResponse.java AI 推荐结果
│   │   │   ├── AuthRequest.java / AuthResponse.java
│   │   │   ├── UserSummaryDto.java
│   │   │   ├── HistoryRecordDto.java / HistoryDetailDto.java
│   │   │   ├── WeightRecordDto.java
│   │   │   ├── ForgotPasswordRequest.java
│   │   │   └── ResetPasswordRequest.java
│   │   ├── ai/                          AI 抽象层
│   │   │   ├── AiService.java            策略接口（含 streamAndRecommend + recognizeFood）
│   │   │   ├── AiServiceFactory.java     工厂 + Mock 降级
│   │   │   ├── MockAiService.java        Mock 实现（含关键词匹配食物识别）
│   │   │   └── OpenAiCompatAiService.java DEEPSEEK 同步+流式+食物识别
│   │   ├── service/                     业务逻辑
│   │   │   ├── NutritionService.java     推荐 + 流式推荐 + 偏好上下文注入
│   │   │   ├── AuthService.java          注册/登录/验证/重置
│   │   │   ├── EmailService.java         邮箱验证/重置密码邮件
│   │   │   ├── UserProfileService.java   保存/读取 4 个目标字段
│   │   │   ├── FoodDiaryService.java     含 updateRecord/getWeeklySummary/attachGoalsAndPercents
│   │   │   ├── FavoriteRecipeService.java ⭐ NEW 收藏增删查（第五周）
│   │   │   ├── FoodPreferenceService.java ⭐ NEW 偏好管理+上下文构建（第五周）
│   │   │   ├── HistoryService.java
│   │   │   ├── WeightService.java
│   │   │   └── CustomUserDetailsService.java
│   │   ├── controller/                  REST API
│   │   │   ├── NutritionController.java  + /api/recommend/stream
│   │   │   ├── AuthController.java       + /api/auth/forgot-password, /reset-password, /verify
│   │   │   ├── FoodDiaryController.java  + PUT 修改 + weekly 汇总 + recognize 识别
│   │   │   ├── FavoriteRecipeController.java ⭐ NEW（第五周，含登录校验）
│   │   │   ├── FoodPreferenceController.java ⭐ NEW（第五周，含登录校验）
│   │   │   ├── WeightController.java
│   │   │   └── HistoryController.java
│   │   └── exception/
│   │       ├── AiApiException.java
│   │       └── GlobalExceptionHandler.java
│   └── src/main/resources/
│       ├── application.yml              MySQL 配置（mysql / prod）
│       └── static/                      前端文件
└── frontend/                           前端开发目录
    ├── index.html
    ├── css/style.css
    └── js/app.js
```

## 技术栈总览

| 层 | 技术 | 版本 |
|----|------|------|
| 后端框架 | Spring Boot | 3.2.0 |
| 编程语言 | Java | 21（⚠️ 必须用 JDK 21） |
| 安全认证 | Spring Security + BCrypt | Session Cookie |
| 数据库 | MySQL 8（默认 mysql profile） | 唯一数据库 |
| ORM | Spring Data JPA + Hibernate | DDL 自动更新 |
| AI | DeepSeek（OpenAI 兼容）| deepseek-chat，支持流式 |
| 邮件 | Spring Boot Mail | SMTP |
| 前端 | 原生 HTML + CSS + JS + Chart.js | Chart.js CDN |
| 流式 | SSE（Server-Sent Events）| 后端 Flux → 前端 fetch + ReadableStream |
| JSON | Jackson + JavaTimeModule | ⭐ 第四周修复 LocalDate/LocalDateTime 序列化 |
| 开发工具 | Spring Boot DevTools | ⭐ 第四周新增：改 CSS/JS/HTML 自动重启 |

> **注意**：DevTools 只在 IDE 直接运行 / `mvn spring-boot:run` 时生效。
> Fat JAR（`java -jar`）模式下 DevTools 被 Spring Boot 自动 disable，不会自动重启。
> 如果要热加载前端文件，推荐在 IDE 里 Run，或用 `mvn spring-boot:run` 启动。

## API 接口清单

### 公开接口（游客可用）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 前端页面 |
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录 |
| GET | `/api/auth/me` | 检查登录状态 |
| POST | `/api/auth/forgot-password` | 忘记密码（发邮件） |
| POST | `/api/auth/reset-password` | 重置密码（token 验证） |
| GET | `/api/auth/verify?token=xxx` | 邮箱验证 |
| POST | `/api/recommend` | AI 推荐（同步） |
| POST | `/api/recommend/stream` | AI 推荐（流式 SSE） |
| ⭐ POST | `/api/recommend/goals` | 营养目标计算（游客可用，无需登录） |
| ⭐ POST | `/api/food-diary/recognize` | AI 食物识别（第五周） |
| GET | `/api/health` | 健康检查 |

### 需登录接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET / PUT / DELETE | `/api/user/profile` | 用户画像（PUT 含营养目标） |
| GET | `/api/recommend/history` | 历史推荐列表 |
| GET | `/api/recommend/history/{id}` | 历史详情 |
| POST | `/api/auth/logout` | 登出 |
| GET / POST / DELETE | `/api/weight` | 体重记录 |
| GET | `/api/weight/stats` | 体重统计 |
| POST | `/api/food-diary` | 新增食物日记 |
| GET | `/api/food-diary` | 日记分页列表 |
| PUT | `/api/food-diary/{id}` | 修改日记 |
| DELETE | `/api/food-diary/{id}` | 删除日记 |
| GET | `/api/food-diary/daily?date=yyyy-MM-dd` | 每日营养汇总 + 达标率 |
| GET | `/api/food-diary/weekly?endDate=yyyy-MM-dd` | 最近 7 天汇总 |
| GET | `/api/food-diary/chart?days=30` | 图表数据 |
| ⭐ GET | `/api/food-preference` | 查询用户全部偏好（第五周） |
| ⭐ POST | `/api/food-preference` | 设置/更新偏好 LIKE/DISLIKE/NEUTRAL（第五周） |
| ⭐ DELETE | `/api/food-preference?foodName=xxx` | 删除偏好记录（第五周） |
| ⭐ GET | `/api/favorite-recipes?page=0&size=20` | 收藏食谱列表（第五周） |
| ⭐ GET | `/api/favorite-recipes/check?recipeName=xxx` | 检查是否已收藏（第五周） |
| ⭐ POST | `/api/favorite-recipes` | 添加收藏（第五周） |
| ⭐ DELETE | `/api/favorite-recipes/{id}` | 删除收藏（第五周） |
| ⭐ DELETE | `/api/favorite-recipes?recipeName=xxx` | 按名称删除收藏（第五周） |

## 第八周功能新增（2026-11-11）

### 1. 游客使用模式 —— 无需登录即可体验「画像 & 推荐」

```
需求：让未注册用户也能快速体验核心能力，降低使用门槛，同时不破坏既有登录体系。

后端：
  · SecurityConfig 白名单放行 /api/recommend、/api/recommend/stream、/api/recommend/goals
  · NutritionController 新增 POST /api/recommend/goals —— 无状态目标计算接口：
      接收 UserProfileRequest，直接调 NutrientGoalService.calculate() 返回
      NutrientGoalDto（BMR / TDEE / BMI / 推荐热量 / 三大营养素 / 公式说明）
      不落库、不建会话、不写用户数据。

前端：
  · index.html 导航栏 + Hero 区各加一个「游客使用」按钮（onclick="enterGuestMode()"）
  · app.js 新增 enterGuestMode()：
      - guestMode = true，currentUser = null
      - 只保留「画像 & 推荐」一个标签，其余全部隐藏
      - 隐藏「保存画像」按钮，导航栏换成「👤 游客模式」徽章 + 登录 / 免费注册入口
      - body 加 .guest-mode 类
  · app.js loadRecommendedGoals() 分流：
      - 登录用户：先 PUT /api/user/profile 保存，再 GET /api/user/profile/recommend-goals
      - 游客：直接 POST /api/recommend/goals，按当前表单数据即时计算，不保存
  · style.css 新增 body.guest-mode（隐藏首屏 Hero、显示应用壳）与 .guest-badge 样式

设计原则：
  · 游客态无状态：不持久化用户数据、不访问任何用户专属接口
  · 与登录态互斥：showLoggedInUI() 会重置 guestMode、恢复全部标签
  · 关闭 / 刷新即回到未登录首页，不留用户痕迹
```

### 2. 看板基础信息栏精简

```
需求：看板「基础信息」栏里的口味偏好 / 食物喜欢 / 食物过敏三项不再展示，
      避免与「便利贴看板」的偏好分区信息重复。

修复：assistant.js 渲染基础信息时，从 items 数组移除
      「👅 口味偏好」「🍽️ 食物喜好」「🚫 食物过敏」三个条目，
      并清理不再使用的 tasteMap / tasteLabel / foodPrefs / foodAllergies 变量。

精简后基础信息栏保留：性别、年龄、年龄段、身高、体重、目标、活动水平、
                       所在地区、每日热量、蛋白质目标、碳水目标、脂肪目标。
```

---

## 第七周稳健性修复（2026-11-11）

> 目标：修复潜在稳健性问题，避免演示 / 日常使用翻车；UI 文案保持原有助手风格，不做"去 AI 味"改写。

### 1. 序数指代推荐菜的餐别不再写死

```
问题：对话里说"就选第一个吧 / 最后一个"，餐别此前硬编码为"午餐"，跨时段会记错餐次。

修复：AssistantIntentService 新增 mealTypeForHour(int hour) 按时段推断餐别：
      5-10 早餐 / 10-14 午餐 / 14-17 加餐 / 17-22 晚餐 / 其余 加餐。
      序数指代分支改用 mealTypeForHour(LocalTime.now().getHour())。
      同时 extractRecipeIndex 关键词表补充"最后那道"。
      序数指代直接映射前端 suggestedRecipes 列表，绕过 AI 食物识别，避免"未知食物 0kcal"。
```

### 2. 食物日记 amountGram 落库空值兜底

```
问题：新增日记时 amountGram 传 null 会原样入库。

修复：FoodDiaryService 新增记录分支改为
      record.setAmountGram(request.getAmountGram() != null ? request.getAmountGram() : 0.0)；
      更新分支保留原语义（显式传 null 仍可清空）。
```

### 3. 核心意图逻辑补充单元测试

```
AssistantIntentServiceTest 由 31 条扩展到 34 条，新增：
  · extractRecipeIndexNumbers    —— 中文 / 阿拉伯数字序号解析
  · extractRecipeIndexGuards     —— 无序号 / 空消息不误命中
  · mealTypeForHourMapping       —— 全时段餐别映射
验证：mvn test 34/34 全部通过，0 失败 0 错误。
```

### 4. 打包部署加固（Fat JAR 修复）

```
问题：spring-boot-maven-plugin 声明时缺 <executions>，repackage goal 未绑定到
      package 阶段，package 产出 380KB 瘦 jar（缺依赖、无 loader），java -jar 无法启动。
      此前"能跑的包"是更早遗留的 Fat JAR。

修复：pom.xml 给 spring-boot-maven-plugin 补 executions 绑定 repackage，
      重新 mvn clean package 生成 57MB Fat JAR。

附带：旧 Java 进程（PID 47664）长期占用 jar 文件句柄，导致 mvn clean 报
      "Failed to delete target\nutri-ai-0.1.0.jar"，停掉旧进程后 clean 正常。
```

---

## 第六周功能详解（2026-10-29）

### 第三梯队功能（全部完成）

#### 1. AI 对话助手 —— 15 种意图直接执行操作

```
AssistantIntentService.chat() 核心流程：

用户输入自然语言 → 关键词/正则意图匹配 → 直接调用 Service 层执行 CRUD
                 → 返回 AssistantChatResponse { reply, actions, tips, data }
                 → 前端渲染：气泡消息 + 动作卡片 + 营养数据卡片

15 种意图类型（按匹配优先级排序）：

  # 意图类型         关键词示例                    执行动作
  ──────────────────────────────────────────────────────────────────
   1 今日汇总        "今天吃了多少" "查进度"         FoodDiaryService.getDailySummary()
   2 本周汇总        "这周数据" "近7天"              FoodDiaryService.getWeeklySummary()
   3 记录体重        "今天 68kg" "刚称 136斤"       WeightTrackerService.addRecord()
   4 查体重          "我现在多重" "体重曲线"          WeightTrackerService.getStats()
   5 冰箱记忆        "我家有鸡胸肉、西兰花"          FridgeService.remember() + 自动推荐
   6 清空冰箱        "清空冰箱"                      FridgeService.forgetAll()
   7 移除食材        "吃完了鸡蛋"                    FridgeService.forgetOne()
   8 喜好/厌恶       "我喜欢吃辣" "讨厌香菜"         FoodPreferenceService.setPreference()
   9 记录饮食        "早餐吃了鸡蛋+牛奶"             AI 识别营养 → FoodDiaryService.addRecord()
  10 营养缺口        "我缺什么" "达标了吗"            AiCoachService.getNutritionGap()
  11 推荐食谱        "今晚吃什么" "减脂餐"            NutritionService / FridgeService 推荐
  12 今日进度        "还能吃多少" "配额"              AiCoachService.getCoachFeedback()
  13 更新画像        "我要减脂" "25岁175cm"           UserProfileService.saveProfile()
  14 帮助            "你能做什么"                    返回 7 条能力清单
  15 AI 兜底         以上都不匹配 → 完整上下文 + AI     拼装画像+偏好+冰箱+今日数据 → 发送 DeepSeek

关键设计：
  · 意图优先于 AI 兜底 —— 能用规则解析的直接执行，不浪费 AI 调用
  · 体重提取三层正则 —— 宽松匹配 → 反排除（食材量/价格）→ 关键词兜底
  · 日期相对解析 —— "大前天/昨天/3天前" 自动转 LocalDate
  · 冰箱触发推荐 —— 新增食材后自动调用 fridgeRecommendation 生成菜谱
```

#### 2. 便利贴看板系统 —— 看板标签 → 左侧 rail → 点击即发送 AI

```
数据结构：
  backend AssistantController.board() 一次性返回三大面板：
    {
      preferences: [ { foodName, preference: LIKE/DISLIKE }, ... ],
      fridge: [ "鸡胸肉", "西兰花", ... ],
      gap: { trackedDays, deficiencies, surpluses, aiInsight }
    }

  前端 rail 区域：localStorage 持久化 nutriai_rail_picks_v2
    { likes: [], dislikes: [], fridge: [], gap: [], custom: [] }

交互设计：
  看板 rail 区：
    ├── 标题 "便利贴" + 空状态提示
    ├── 自定义输入框（回车/✓ 按钮 → addToPicks('custom', val)）
    ├── 清空按钮（clearRail()，确认后清空 localStorage，不删看板数据）
    └── rail-chips 容器：
        ├── 每个 chip 可拖动 draggable="true"
        ├── 拖入 rail → 加入便利贴 localStorage（看板原始数据不动）
        ├── 拖出 rail → 可移除
        ├── 点击 → dropSticker() → sendQuick() → sendChat()
        │          （不再是填充输入框，而是直接发送给 AI！）
        └── × 删除按钮常驻半透明可见（rc-remove，hover 变亮）

  看板区域（右侧）：
    ├── 💚 喜欢 面板 —— 显示 FoodPreference LIKE 记录
    ├── 🚫 厌恶 面板 —— 显示 FoodPreference DISLIKE 记录
    ├── 🧊 冰箱 面板 —— 显示 FridgeService.list()
    ├── 🎯 缺口 面板 —— 显示 NutritionGapDto 偏低/偏高项
    ├── 每个面板右上角有 [+] inline 添加按钮
    └── 每个 sticker 可拖到左侧 rail 加入便利贴

API：
  GET  /api/assistant/board              → 一次性返回三大面板数据
  POST /api/assistant/chat                → AI 对话（意图识别 + 兜底）
  POST /api/assistant/fridge/remember     → 批量添加冰箱食材
  POST /api/assistant/fridge/forget       → 移除单个或清空全部
```

#### 3. 导航布局修复 —— fixed tab 双层固定布局

```
问题：app-tabs 导航栏遮挡页面内容顶部

根因：
  旧方案：app-tabs 用 position: sticky，但上方还有固定的 app-shell 顶部
         sticky 元素受父容器约束，层级冲突导致遮挡

修复：
  style.css —— 改为双层固定：
    .app-shell { padding-top: 140px; }    ← 给内容区预留空间
    .app-tabs  { position: fixed; top: 62px; z-index: 50; }
    body.logged-in { ... }                 ← 登录态 body 类标记
  app.js showLoggedInUI() 添加 document.body.classList.add('logged-in')

为什么加 body.logged-in？
  未登录时页面有 Hero 大标题区，需要足够的顶部空间
  登录后进入工作台，不需要 Hero 区，body.logged-in 可用于 CSS 控制隐藏
```

#### 4. Bug 修复全景（第五周末 → 第六周初密集修复）

| # | 问题 | 根因 | 修复 |
|---|------|------|------|
| 1 | **assistant.js 全文件中文双重编码损坏** | 工具链写文件时编码错乱，所有 `'我喜欢'` → `'鎴戝枩娆?'` | 完全重写文件为干净 UTF-8，同步到 static/ 目录 |
| 2 | **看板标签点击/删除完全静默失败** | `JSON.stringify()` 输出双引号 `"xxx"`，嵌套在 `onclick="..."` 双引号属性里 → HTML 解析爆炸 | 加 `escAttr()` 函数，所有 inline onclick 改用单引号包裹 `onclick='...'` |
| 3 | **看板 inline + 按钮添加无反应** | 同 #2，`onkeydown="inlineAddKey(event,'likes')"` 里引号嵌套 | 同 #2 统一修复 |
| 4 | **便利贴功能页面加载后完全不渲染** | assistant.js 只定义了 `refreshStickers()` 函数，但无任何调用入口 | 添加 `DOMContentLoaded` 监听器，500ms 后显式调用 |
| 5 | **`escAttr is not defined`** | 代码里用了不存在的 `escAttr()` 函数（之前版本遗留） | 替换为已有的 `escHtml()`，后改为新增 `escAttr()` 专门用于属性转义 |
| 6 | **Maven clean 失败：无法删除 target/classes** | 旧 Java 进程占用文件句柄 | `Get-Process java \| Stop-Process -Force` 强制杀掉 |
| 7 | **刷新修改不生效** | 误以为改 static/ 目录生效，但 start.bat 会用 frontend/ 同步覆盖 static/ | 改 frontend/ 目录 → 重新 `mvn package` → 复制到 static/ → 启动 |

---

## 第五周功能详解（2026-10-20）

### 第二梯队功能（全部完成）

#### 1. AI 食物识别（POST /api/food-diary/recognize）

```
用户输入自然语言描述（如"一碗米饭200g + 番茄炒蛋150g"）
   → POST /api/food-diary/recognize { description: "..." }
   → AiService.recognizeFood(description) 
   → DEEPSEEK / Mock 解析返回 FoodRecognitionResult
   → 前端自动填充：foodName、mealType、calories、protein、carbs、fat、servingSize

MockAiService 关键词匹配规则：
  - "米饭" → 200g=232kcal, 蛋白5.2g, 碳水51.g, 脂肪0.6g
  - "番茄" "蛋" → 150g=120kcal, 蛋白8g, 碳水8g, 脂肪7g
  - "鸡胸" → 150g=198kcal, 蛋白35g, 碳水0g, 脂肪5g
  - "牛奶" → 250ml=155kcal, 蛋白8g, 碳水12g, 脂肪8g
  - 无匹配 → 通用值 100kcal

FoodRecognitionResult 结构：
  ├── foodName        String   食物名称
  ├── mealType        String   餐次推断（BREAKFAST/LUNCH/DINNER/SNACK）
  ├── calories        int      热量 kcal
  ├── proteinGrams    double   蛋白质 g
  ├── carbsGrams      double   碳水 g
  ├── fatGrams        double   脂肪 g
  └── servingSize     String   份量描述
```

#### 2. 食物偏好系统 LIKE / DISLIKE / NEUTRAL

```
FoodPreference 实体：
  ├── id            BIGINT PK
  ├── user          @ManyToOne LAZY (@JsonIgnore)
  ├── foodName      VARCHAR  食物/食谱名称
  ├── category      VARCHAR  类别（"recipe"/"food"）
  ├── preference    VARCHAR  偏好值 LIKE / DISLIKE / NEUTRAL
  ├── createdAt     TIMESTAMP
  └── updatedAt     TIMESTAMP

API:
  GET    /api/food-preference               → 当前用户全部偏好
  POST   /api/food-preference               → 设置偏好 { foodName, category, preference }
  DELETE /api/food-preference?foodName=xxx  → 删除偏好

前端按钮（每个食谱卡片底部）：
  [👍 喜欢]   → POST preference=LIKE（已标记则切换 NEUTRAL）
  [👎 避开]   → POST preference=DISLIKE（已标记则切换 NEUTRAL）
  [  ·  ]    → 未标记（默认中性）

requireLogin() 校验：未登录统一返回 401 "请先登录"
```

#### 3. 偏好影响推荐生成

```
FoodPreferenceService.buildPreferenceContext(userId)
    ├── 查询用户全部 LIKE/DISLIKE 记录
    ├── 构建系统提示词：
    │   "用户偏好数据：\n喜欢的食物：...\n不喜欢的食物：...\n
    │    请参考用户偏好调整推荐内容。\n"
    └── 返回拼接到 AI Prompt 上下文中

NutritionService.getRecommendations() / streamRecommendations()
    → 如果 usePreferences=true：
        → 调用 buildPreferenceContext(userId)
        → 追加到系统 Prompt 尾部
        → AI 生成推荐时自动避开 DISLIKE 食材、优先 LIKE 食材

前端开关：
    🧠 偏好影响 [✓] ← 复选框 #prefToggle
    在推荐表单中，勾选后才注入偏好上下文
```

#### 4. 食谱收藏系统

```
FavoriteRecipe 实体：
  ├── id          BIGINT PK
  ├── user        @ManyToOne LAZY (@JsonIgnore)
  ├── recipeName  VARCHAR      食谱名称
  ├── recipeJson  TEXT          完整食谱 JSON（用于展示+重新加入食物日记）
  ├── summary     VARCHAR(512)  食谱摘要描述
  └── createdAt   TIMESTAMP

API:
  GET    /api/favorite-recipes?page=0&size=20  → 分页列表
  GET    /api/favorite-recipes/check?recipeName=xxx → 是否已收藏
  POST   /api/favorite-recipes                    → 添加收藏
  DELETE /api/favorite-recipes/{id}              → 删除收藏
  DELETE /api/favorite-recipes?recipeName=xxx    → 按名称删除

前端：
  - 每个食谱卡片有 ⭐ 收藏按钮，点击添加/取消
  - 收藏列表：独立区域展示已收藏食谱，支持分页加载（每页10条）
  - 显示食谱名称、摘要、收藏日期
```

#### 5. 打字机模式增强

```
问题：打字机（SSE）模式直接渲染 markdown，没有食谱卡片 → 没有喜欢/避开/收藏按钮

解决方案：
  1. typo机流完成（收到 [DONE]）后，streamSection 头部自动显示
     [📋 查看结构化食谱] 按钮
  2. 点击按钮 → 调用非流式 recommend() 接口
     → renderResult() 渲染带按钮的食谱卡片
     → 同时自动保存到推荐历史
  3. currentRecPayload 变量缓存当前请求参数，供 loadStructuredResult() 使用
```

#### 6. 数据库新增表

```sql
CREATE TABLE food_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    food_name VARCHAR(255) NOT NULL,
    category VARCHAR(50),
    preference VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE favorite_recipes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    recipe_name VARCHAR(255) NOT NULL,
    recipe_json TEXT,
    summary VARCHAR(512),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

## 第四周功能详解（2026-10-15）

### 第一梯队功能（全部完成）

#### 1. 食物日记 CRUD + 修改（PUT /api/food-diary/{id}）

```
FoodDiary 实体：
├── foodName      VARCHAR(100)  食物名称（必填）
├── mealType      VARCHAR(20)   餐型 BREAKFAST/LUNCH/DINNER/SNACK
├── calories      DOUBLE        热量 kcal
├── protein       DOUBLE        蛋白质 g
├── carbs         DOUBLE        碳水 g
├── fat           DOUBLE        脂肪 g
├── amountGram    DOUBLE        克数（可选）
├── recordDate    DATE          记录日期（默认今天）
└── note          VARCHAR(200)  备注

Repository 索引：idx_fooddiary_user_date ON (user_id, record_date DESC)
Service 权限校验：只能修改/删除自己的记录（403 Forbidden）
```

#### 2. 每日营养目标设定

在 UserProfile 实体上新增 4 个字段：
```java
private Double dailyCalorieGoal;   // 热量目标 kcal
private Double proteinGoal;        // 蛋白质目标 g
private Double carbsGoal;          // 碳水目标 g
private Double fatGoal;            // 脂肪目标 g
```
前端通过 `PUT /api/user/profile` 一起提交，Hibernate `ddl-auto: update` 自动 ALTER TABLE 加列。

#### 3. 每日营养达标率计算（attachGoalsAndPercents）

```
FoodDiaryService.getDailySummary(userId, date)
    ├── 汇总当天所有 FoodDiary 的 calories/protein/carbs/fat
    ├── attachGoalsAndPercents()
    │   ├── 读取 UserProfile 的 4 个 Goal
    │   └── calcPercent(actual, goal) → round(actual/goal*100, 1)
    └── 返回 DailyNutritionDto：
        ├── totalCalories / totalProtein / totalCarbs / totalFat（实际）
        ├── calorieGoal / proteinGoal / carbsGoal / fatGoal（目标）
        └── caloriePercent / proteinPercent / carbsPercent / fatPercent（达标率%）
```

#### 4. 周汇总接口（GET /api/food-diary/weekly）

```
FoodDiaryService.getWeeklySummary(userId, endDate)
    ├── 默认 endDate = LocalDate.now()，start = endDate - 6
    ├── findByUserIdAndRecordDateBetween(userId, start, end)
    ├── 遍历 7 天（即使某天 0 记录也返回空 DailyNutritionDto）
    ├── 每天独立 attachGoalsAndPercents 计算达标率
    └── 返回 List<DailyNutritionDto> 长度固定 7
```

#### 5. 地域特色推荐（UserProfile.region）

```
数据流转：
前端表单 → 用户选择地区（如"广东广州"）
    → PUT /api/user/profile 保存 region 字段
    → NutritionService 构建 AI Prompt 时自动拼接：
        "该用户所在地区为 [广东广州]，
         推荐时优先融入当地特色食材和烹饪风格"
    → AI 返回自动产出粤式/川式/西北式菜谱
```

#### 6. 前端 UI 全面美化（2026-10-15）

```
素材：全部使用 CDN 托管的开源免费资源（零版权风险）
  - Google Fonts Inter（免费可商用）
  - Font Awesome Free 6.5.2（SIL OFL 1.1 + CC BY 4.0）
  - Chart.js（MIT License，用于体重折线图渐变）
  - 所有视觉效果为纯 CSS 渐变/阴影/动画，无第三方图片

改动：
  style.css — 完全重写
    ├── 配色：绿色系 #10b981 为主，琥珀/蓝/粉为营养素独立色
    ├── 毛玻璃导航栏（backdrop-filter: blur(20px)）
    ├── 渐变背景 + 卡片阴影 + 悬停上移动画
    ├── 🆕 进度条组件（根据达标率渲染，超 100% 红标，低于 50% 琥珀警告）
    ├── 🆕 餐次彩色标签（早餐金 / 午餐绿 / 晚餐蓝 / 加餐粉）
    └── 平滑过渡 transition
  
  index.html — 引入字体 + 图标 + 进度条容器
  app.js — 723 行全面升级渲染函数
```

### 测试数据示例（2026-10-15 demo2026 账户）

```
账户: demo2026 / NutriAI@123
地区: 广东广州
目标: 热量2000 / 蛋白80 / 碳水250 / 脂肪65

早餐: 燕麦鸡蛋粥  380 kcal  蛋白18g  碳水52g  脂肪10g
午餐: 加大版鸡胸肉沙拉  620 kcal  蛋白55g  碳水35g  脂肪22g
加餐: 一杯牛奶  150 kcal  蛋白8g  碳水12g  脂肪8g
晚餐: 清蒸鲈鱼 + 白灼菜心  420 kcal  蛋白42g  碳水20g  脂肪18g
───────────────────────────────────────────────
总计: 1570 kcal  蛋白123g  碳水119g  脂肪58g
达标率: 78.5%  153.8% 🔴超量!  47.6% 🟡偏低  89.2%
```

### 预置测试账户

| 用户名 | 密码 | 用途 |
|--------|------|------|
| `demo2026` | `NutriAI@123` | ✅ 已预置完整数据（见上），用于截图 / 演示 / 测试 |

## 数据库表结构

### food_diaries（第四周）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 记录 ID |
| user_id | BIGINT FK → users.id NOT NULL | 关联用户 |
| food_name | VARCHAR(100) NOT NULL | 食物名称 |
| meal_type | VARCHAR(20) | 餐型 |
| amount_gram | DOUBLE | 克数 |
| calories | DOUBLE | 热量 kcal |
| protein | DOUBLE | 蛋白质 g |
| carbs | DOUBLE | 碳水 g |
| fat | DOUBLE | 脂肪 g |
| record_date | DATE | 记录日期 |
| note | VARCHAR(200) | 备注 |
| created_at | TIMESTAMP | 创建时间 |

**索引**: `idx_fooddiary_user_date ON (user_id, record_date DESC)`

### food_preferences ⭐ 第五周新增

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 记录 ID |
| user_id | BIGINT FK → users.id NOT NULL | 关联用户 |
| food_name | VARCHAR(255) NOT NULL | 食物/食谱名称 |
| category | VARCHAR(50) | 类别 recipe/food |
| preference | VARCHAR(20) NOT NULL | LIKE/DISLIKE/NEUTRAL |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

### favorite_recipes ⭐ 第五周新增

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 记录 ID |
| user_id | BIGINT FK → users.id NOT NULL | 关联用户 |
| recipe_name | VARCHAR(255) NOT NULL | 食谱名称 |
| recipe_json | TEXT | 完整食谱 JSON |
| summary | VARCHAR(512) | 食谱摘要 |
| created_at | TIMESTAMP | 收藏时间 |

### user_profiles ⭐ 第四周扩展

| 新增字段 | 类型 | 说明 |
|---------|------|------|
| daily_calorie_goal | DOUBLE | 每日热量目标 kcal |
| protein_goal | DOUBLE | 每日蛋白质目标 g |
| carbs_goal | DOUBLE | 每日碳水目标 g |
| fat_goal | DOUBLE | 每日脂肪目标 g |
| region | VARCHAR(100) | ⭐ 所在地区（广东广州 / 四川成都 等），用于地域特色推荐 |

### users（第三周扩展）

| 新增字段 | 类型 | 说明 |
|---------|------|------|
| email_verified | BOOLEAN DEFAULT false | 邮箱是否验证 |
| verification_token | VARCHAR(100) | 邮箱验证 token |
| verification_token_expiry | TIMESTAMP | 验证 token 过期时间 |
| reset_password_token | VARCHAR(100) | 重置密码 token |
| reset_password_token_expiry | TIMESTAMP | 重置 token 过期时间 |

### weight_records（第三周）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AUTO_INCREMENT | 记录 ID |
| user_id | BIGINT FK → users.id | 关联用户 |
| weight_kg | FLOAT NOT NULL | 体重 |
| record_date | DATE | 记录日期 |
| note | VARCHAR(200) | 备注 |
| created_at | TIMESTAMP | 创建时间 |

## 环境变量

> 所有变量在 `start.bat` 里配置，或通过 `-Dkey=value` 启动参数覆盖。
> 默认 profile 为 mysql，请确保本机 MySQL 已启动（默认 root/bingxin）。

| 变量 | 默认 | 说明 |
|------|------|------|
| `AI_API_KEY` | 空（→ MockAiService） | DEEPSEEK API Key |
| `SPRING_PROFILE` | `mysql`（application.yml 默认值） | `mysql` / `prod`（均为 MySQL） |
| `MYSQL_HOST` | `localhost` | MySQL 主机 |
| `MYSQL_PORT` | `3306` | MySQL 端口 |
| `MYSQL_USER` | `root` | MySQL 用户 |
| `MYSQL_PASSWORD` | `bingxin` | 本机开发默认；生产环境请用环境变量覆盖 |
| `MYSQL_DB` | `nutriai` | MySQL 数据库名 |
| `MAIL_HOST` | `smtp.qq.com` | SMTP 服务器 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | 空 | SMTP 账号密码 |

## 遇到的坑 & 修复

### 第五周

| # | 问题 | 原因 | 修复 |
|---|------|------|------|
| 1 | 收藏食谱列表什么都不显示 | `FavoriteRecipe.user` 是 `FetchType.LAZY`；Jackson 序列化时事务已关闭，触发 `LazyInitializationException` → 500 错误 → 前端收到 `data: null` | 实体 `user` 字段加 `@JsonIgnore` 注解，跳过序列化 |
| 2 | 食物偏好列表空 | `FoodPreference.user` 同样 LAZY + 序列化失败，同上 | 实体 `user` 字段加 `@JsonIgnore` |
| 3 | 喜欢/避开/收藏按钮按了没反应 | `FoodPreferenceController` 和 `FavoriteRecipeController` 没检查登录状态，未登录时 `userId=null` → NPE → 500 | 新增 `requireLogin()` 方法，未登录直接抛 401 + 前端加 `if (!currentUser)` 检查并显示明确提示 |
| 4 | 打字机模式没有喜欢/避开/收藏按钮 | 打字机直接渲染原始 markdown，从未调用 `renderResult()` | 流完成后显示「📋 查看结构化食谱」按钮，点击调用非流式接口渲染带按钮的食谱卡片 |
| 5 | 推荐历史显示"0个食谱" | `HistoryRecordDto` 缺少 `recipesCount` 字段；前端取值路径 `data.data.response` 错误 | DTO 加字段解析 `responseJson.recipes.length`；前端改为 `data.data.recommendation` |

### 第四周

| # | 问题 | 原因 | 修复 |
|---|------|------|------|
| 1 | `java.time.LocalDate` 反序列化失败 500 | `FoodDiaryRequest.recordDate` 没有日期格式注解 | 加 `@JsonFormat(pattern = "yyyy-MM-dd")` |
| 2 | `java.time.LocalDateTime` 序列化失败 500 | **自定义 `@Primary ObjectMapper` Bean 覆盖了 Spring Boot 自动配置，没注册 `JavaTimeModule`** | `WebConfig.objectMapper()` 加 `.registerModule(new JavaTimeModule()).disable(WRITE_DATES_AS_TIMESTAMPS)` |
| 3 | MySQL `Unsupported character encoding 'utf8mb4'` | JDBC URL 用了 MySQL 专属编码名，但 Java charset 库没有 | 改成 `characterEncoding=UTF-8`（JDBC 驱动自动映射为 utf8mb4） |
| 4 | MySQL 服务启动 Permission denied | Windows 当前用户无权限访问 `C:\ProgramData\MySQL\MySQL Server 8.0\Data\` | 需要管理员权限启动 |
| 5 | Spring Boot Fat JAR 直接 `jar uf` 更新 class 后 `ClassNotFoundException` | Spring Boot 3 的 Fat JAR 结构是 `BOOT-INF/classes/`，不是根目录，jar uf 把文件写到错误路径 | 用 Python zipfile 解压→过滤旧 class→添加新 class→重打包；不要用 `jar cf` 整体重打包（会丢 Spring-Boot-* Manifest 和 classpath.idx） |
| 6 | javac 编译时 lombok 找不到 | 直接用 target/classes 作为 -cp 没包含依赖 jar | 从 BOOT-INF/lib 提取所有 jar 拼成 classpath，同时 lombok.jar 要在 -cp 和 -processorpath 都加 |

### 第三周

| # | 问题 | 原因 | 修复 |
|---|------|------|------|
| 1 | 流式一直卡"连接中" | 流式用了同步的 JSON prompt | 新增 STREAM_SYSTEM_PROMPT 用自然中文 |
| 2 | 流式返回 200 但 body 空 | `split("\n")` 假设 SSE 格式 | 用 StringBuilder 累积 buffer 到 `\n\n` |
| 3 | bodyToFlux 收不到 `\n\n` | DEEPSEEK 返回裸 JSON 不是 SSE | mapNotNull 直接按 JSON 对象解析 |
| 4 | JSON 尾部逗号反序列化失败 | AI 偶尔返回 `{"a":1,}` | 加 ALLOW_TRAILING_COMMA + removeTrailingCommas |

## Spring Boot Fat JAR 更新技巧（第四周学到的）

**不要用** `jar uf app.jar new.class`，它会把 class 写到 jar 根目录。
**正确方式**：用 Python zipfile 工具（跨平台、无额外依赖）：

```python
import zipfile, os

jar_path = 'target/nutri-ai-0.1.0.jar'
files_to_update = [
    ('classes/com/nutriai/config/WebConfig.class',
     'BOOT-INF/classes/com/nutriai/config/WebConfig.class'),
]
target_basenames = {os.path.basename(dst) for _, dst in files_to_update}

seen = set()
with zipfile.ZipFile(jar_path, 'r') as zin:
    with zipfile.ZipFile(jar_path + '.new', 'w', zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            # 跳过 BOOT-INF/classes 下同名旧文件 + 根目录错误的 com/
            if item.filename.startswith('BOOT-INF/classes/') and \
               os.path.basename(item.filename) in target_basenames:
                continue
            if item.filename.startswith('com/'):
                continue  # 清除之前错误添加的根目录 com/
            if item.filename in seen:
                continue
            seen.add(item.filename)
            zout.writestr(item, zin.read(item.filename))
        for src, dst in files_to_update:
            with open(src, 'rb') as f:
                zout.writestr(dst, f.read())
os.replace(jar_path + '.new', jar_path)
```

关键注意事项：
- 路径必须是 `BOOT-INF/classes/com/...` 层级
- 不要用 `jar cf` 整体重打包（会丢 Manifest 的 Spring-Boot-* 属性和 classpath.idx）
- 先备份 `.bak`，更新后对比大小和路径列表验证

## 下一步可以做

### ✅ 已完成（2026-10-29 第六周）
- [x] AI 对话助手（15 种意图识别 + 三层兜底，自然语言直接执行 CRUD）
- [x] 意图优先于 AI 兜底的设计（规则解析直接执行，不浪费 API 调用）
- [x] 体重提取三层正则（宽松匹配 → 反排除 → 关键词兜底）
- [x] 相对日期解析（大前天/昨天/3天前 → LocalDate）
- [x] 便利贴看板系统（三大面板一次性 board API）
- [x] 左侧 rail 便利贴区（localStorage 持久化 + 自定义 + 清空）
- [x] 看板 ↔ rail 拖动交互（拖入加入便利贴，看板原始数据不动）
- [x] 便利贴点击即发送 AI（不再填充输入框，直接 sendChat）
- [x] inline + 按钮添加标签（看板面板内直接新增喜欢/厌恶/冰箱）
- [x] 导航布局修复（fixed tab 双层固定 + body.logged-in 类标记）
- [x] assistant.js 编码损坏修复（全文件重写干净 UTF-8）
- [x] JSON.stringify 双引号嵌套爆炸修复（escAttr + onclick 单引号包裹）
- [x] DOM 初始化缺失修复（DOMContentLoaded 显式调用）
- [x] escAttr / escHtml 混用修复（统一转义函数）

### ✅ 已完成（2026-10-20 第五周）
- [x] AI 食物识别（自然语言 → 自动填充营养数据）
- [x] 食物偏好系统（👍喜欢/👎避开/·中性，三种状态）
- [x] 偏好影响开关（勾选后 AI 推荐参考用户历史偏好）
- [x] 食谱收藏系统（⭐收藏 → 独立收藏页面 → 分页查看/删除）
- [x] 打字机模式增强（流完成后可切换为带按钮的结构化卡片）
- [x] LAZY 字段序列化修复（FavoriteRecipe / FoodPreference 加 @JsonIgnore）
- [x] Controller 登录校验（requireLogin() 统一 401 处理）
- [x] 推荐历史修复（recipesCount + 详情字段路径修复）

### ✅ 已完成（2026-10-15 第四周）
- [x] MySQL 持久化（profile=mysql，默认数据库）
- [x] MySQL prod profile 配置（HikariCP 连接池优化）
- [x] 前端食物日记 UI（CRUD 表单 + 四种餐型标签）
- [x] 营养目标设置面板 + 达标率进度条可视化
- [x] 地域特色推荐（UserProfile.region → AI Prompt）
- [x] 7 天周汇总 API（固定返回 7 天）
- [x] 前端 UI 全面美化（绿色系渐变 + 毛玻璃 + Font Awesome + Chart.js 渐变图表）
- [x] Spring Boot DevTools（改 CSS/JS/HTML 自动重启加载）
- [x] 预置测试账户 demo2026（含完整数据 + 超量/偏低达标率示例）

### 📌 还没做
- [ ] 登录状态持久化（刷新页面保持登录 —— 已有但需验证 session 策略）
- [x] 周汇总图表（Chart.js 7 天热量柱状图 + 营养素趋势折线图 + 周统计卡片）
- [ ] AI 智能建议自动推送（"你今日碳水偏低，建议下一餐加一碗杂粮饭"）
- [ ] 响应式布局手机端适配
- [ ] Docker Compose（Spring Boot + MySQL + Nginx）
- [x] 核心意图逻辑单元测试（AssistantIntentServiceTest 34/34 通过，2026-11-11）
- [ ] 集成测试（MockMvc + WireMock，全链路）
- [ ] PWA 离线支持
- [ ] 便利贴 rail 跨设备同步（后端存储，当前是 localStorage 本地）
- [x] 对话历史持久化（localStorage 存储 + 刷新恢复 DOM + 恢复 AI 上下文）（当前仅前端内存）
- [x] 意图识别多轮上下文（matchIntent 结合 history，补充型消息拼装上一轮上下文 + 餐别继承上一轮）
- [ ] 语音输入增强（当前基础 Web Speech API，需加错误处理 + 多语言）