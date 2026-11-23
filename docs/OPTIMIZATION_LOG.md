# NutriAI 优化整理记录

> 日期：2026-11-09
> 范围：构建环境、后端逻辑、前端交互与视觉、项目结构整理
> 验证：`mvn clean package` 通过；单元测试 16/16 通过；本地 MySQL 8 启动并完成登录 / 看板 / 对话 / 推荐 / 日记 / 体重 / 收藏 / CSV 冒烟测试。

---

## 一、构建与环境

| 项 | 调整 |
|----|------|
| Java | `pom.xml` 由 Java 17 升级为 **Java 21**（`release=21`），新增 `-parameters` 与 Lombok 注解处理器配置 |
| 默认 profile | `application.yml` 固定为 **mysql**，连接本机 MySQL 的 `nutriai` 库（`createDatabaseIfNotExist=true` 自动建库） |
| 持久化 | 全部数据存储在 MySQL；H2 依赖与 dev profile 已彻底移除 |
| 演示数据 | 新增 `DemoDataInitializer`：库中无 `demo2026` 时创建演示账号（画像 + 3 天饮食 + 体重） |
| 会话 | 会话调整为 7 天；Cookie 名称 `NUTRIAI_SESSION`，`HttpOnly=true`，支持 `SESSION_COOKIE_SECURE` |
| 流式超时 | 增加 `spring.mvc.async.request-timeout=300000`，避免长回复被 30s 截断 |
| 启动脚本 | 重写 `start.bat / start.ps1 / start-all.bat / stop.bat`：使用 `D:\java\21`、自动构建、按端口停止、移除硬编码 API Key |
| 文档 | 新增 `README.md`；本文件记录优化明细 |
| 目录 | 根目录 PPT 归档至 `docs/reports/`，日志/临时脚本归档至 `scrap/`，根目录仅保留必要文件 |

## 二、后端 BUG 修复（确认可复现）

1. **注册后不建立会话**：注册成功但 `/api/auth/me` 仍 401。`AuthController.register` 现在注册后自动登录并保存 SecurityContext。
2. **对话流式双倍调用 AI**：`chatStream()` 先同步调用 `chat()`（AI 兜底已请求一次），随后又发起流式请求。现在仅对非兜底意图走同步分支，自由问答只调用一次。
3. **冰箱确认卡写入 0 条 / 404**：后端生成 `ingredients`，接口读取 `items`；且前端再次拼接 `/api` 产生 `/api/api/assistant/...`。统一为 `items`，并在前端规范化 endpoint 前缀。
4. **偏好确认卡 404 / 多食物合并**：`/api/food-preferences` → `/api/food-preference`；每个食物拆成独立确认卡，避免存成“鸡胸肉,西兰花”一条。
5. **画像确认卡 405**：确认卡未指定 method，PUT 接口收到 POST。现在显式 `method=PUT`；`UserProfileDto` 增加参数校验。
6. **邮箱登录 500**：认证支持邮箱，但登录后仍按用户名查询。现在按用户名或邮箱回查。
7. **Mock 模式回显隐私上下文**：未配置 AI Key 时把画像/偏好/日记拼进回复。`MockAiService.chatWithContext` 改为固定引导话术，不再回显上下文。
8. **CORS 重复配置**：删除 `WebConfig` 中与 Spring Security 冲突的 `CorsFilter`；默认仅同源，跨域需 `CORS_ORIGINS` 显式配置。
9. **异常处理不完整**：补齐 400（JSON 解析/参数缺失/类型错误）、404、405、409、403、**502（AI 上游错误）** 的映射，避免全部落到 500。
10. **CSV 只导出 30 天**：日记/体重导出改为全部记录；增加 Excel 公式注入防护（`= + - @` 前缀）与 UTF-8 文件名。
11. **今日汇总判断错误**：`totalCalories != null` 恒真，导致“共 0 条 · 0 kcal”。改为按 `totalItems > 0` 判断。
12. **体重为空时回复 null**：查询无记录时先判断 `totalRecords == 0`，输出友好提示。
13. **碳水缺口建议错误**：`碳水` 误传 `proteinLike=true`，建议变成“补瘦肉”。现在走碳水分支；今日目标读取不再依赖当天有无记录。
14. **7 天达标率口径错误**：只记录 3 天时目标仍按 7 天平均，达标率被低估。统一按“有目标的天数”平均。
15. **RAG 中文检索失效**：`\w` 只匹配 ASCII，中文 bigram 永远为空；还会把标点当有效词。改为 `\p{L}\p{N}` 分词，并过滤 0 分结果。
16. **意图抢占**：
    - “我吃完了午饭”不再被当成“从冰箱移除”；
    - “看看冰箱里有啥”不再被今日汇总吞掉；
    - “我家有鸡胸肉，今晚吃什么”优先走推荐；
    - 去掉“确认/搞定”这类泛词触发的食物识别。
17. **体重解析**：取最后一次命中（“从 70kg 减到 68kg”记 68）；按命中单位处理斤/两；价格与食材量排除；拒绝未来日期。
18. **冰箱食材切分**：先剥离数量单位、按“和/跟/与/、”切分、过滤纯数字，避免“200g”“斤苹果”入库。
19. **偏好解析**：支持“喜欢 A 和 B”“喜欢 A 但讨厌 B”“不吃香菜”；剥离否定/停用词，避免把“推荐”“这个”当食物。
20. **热量目标解释文本**：`protein/65.0` 硬编码体重改为真实体重；BMI/蛋白系数除零保护。
21. **N+1 查询**：7 天汇总只查一次画像；食谱本 mealType 过滤下推数据库分页；日记日期边界由 `>` 改为 `>=`。
22. **标签/昵称返回值语义**：AI 角色接口未登录由 500 改为 401；业务错误统一 HTTP 状态码。
23. **忘记密码链路**：未注册邮箱不再返回 404（避免账号枚举）；新增 `/reset-password` 静态重置页与路由。
24. **token 日志**：验证/重置 token 从 INFO 日志移除，仅 DEBUG 可见。

## 三、前端 BUG 修复

1. **XSS（P0）**：用户消息、昵称、日记/收藏/历史/食谱本/AI 推荐内容统一转义；Markdown 渲染增加 HTML 清洗（移除 script/iframe/内联事件/危险协议）。
2. **流式确认卡不出现**：流式分支只渲染 `actions/data/tips`，遗漏 `pendingActions`。新增 `renderPendingActions()` 并在流式与降级路径复用。
3. **SSE 丢换行/分帧错误**：按空行分帧、多行 `data:` 以 `\n` 合并，兼容 `\r\n\r\n`。
4. **HTML 结构错误导致聊天布局塌陷**：`#chatMessages` 被误写成自闭标签，快速按钮/输入框被挤出聊天卡。已修复，聊天布局恢复正常。
5. **历史详情“没反应”**：详情渲染在隐藏的「画像 & 推荐」视图。现在先 `switchMainTab('profile')` 再渲染。
6. **UTC 日期差一天**：所有默认日期改用本地 `localToday()`。
7. **日记日期筛选只影响摘要**：后端列表接口新增 `date` 参数，列表/加载更多均跟随筛选日期。
8. **批量偏好 404**：`/api/preferences/like` → `/api/food-preference`，逐条提交并汇总失败数。
9. **目标进度死逻辑**：修正 `LOSE_WEIGHT` 等枚举大小写；删除不存在的 `targetWeightKg` 死分支。
10. **收藏状态不准确**：渲染后批量查询 `/favorite-recipes/check`，按钮初始态与取消收藏行为正确；移除按名称拼接 CSS 选择器的注入风险。
11. **历史搜索只搜当前页**：后端新增 `q` 参数，前端 300ms 防抖并走服务端分页。
12. **中文输入法回车误发送**：聊天、便利贴、内联添加均判断 `isComposing / keyCode 229`。
13. **并发发送与 typing 气泡**：发送期间加锁；首个 token 到达即移除“正在输入”；追问卡只保留最新一张。
14. **toast 被旧定时器提前隐藏**：保存并清理 timer。
15. **表单数值校验**：日记营养值显式校验 `Number.isFinite && >= 0`，不再把 0 变成 null。
16. **CSV 下载失败无提示**：改为 fetch → blob，401/500 均给出明确提示。
17. **图表主题**：Chart.js 颜色随深色模式切换；缺少 Chart.js 时显示降级提示而不是报错中断。
18. **CDN 依赖脆弱**：marked 固定为 `12.0.2` 并加 `defer`；缺失时自动降级为安全纯文本；`marked.setOptions` 不再阻塞整个脚本。
19. **推荐结果注入/转义**：食谱卡改为 `data-*` + 事件委托，食谱 JSON 存内存缓存，避免内联 onclick 属性被引号截断。
20. **收藏详情**：`alert(JSON)` 改为可滚动详情弹窗。
21. **401 处理**：统一封装 `apiFetch`，未登录/网络错误有明确提示。

## 四、界面美化与可用性

- **首屏**：未登录时隐藏工作台，新增 Hero CTA「免费开始记录 / 我已有账号」，避免半截聊天界面。
- **导航**：登录后导航栏改为深色玻璃，品牌与用户信息对比度恢复正常。
- **深色模式**：补齐表单、卡片、日记、体重、历史、收藏、食谱本、弹窗、图表、看板 rail 的深色样式；首屏在绘制前恢复主题，消除闪白。
- **移动端**：8 个标签横向滚动；工具栏不再整体隐藏；聊天高度使用 `100dvh`；卡片与统计网格自适应；触控目标放大。
- **可访问性**：弹窗 `role=dialog/aria-modal`、消息区 `aria-live`、toast `role=status`、Esc 关闭、键盘可操作历史项、`:focus-visible`、`prefers-reduced-motion`。
- **细节**：修复固定导航锚点遮挡（`scroll-padding-top`）、toast 遮挡输入框、食谱本 320px 断点溢出、营养缺口“偏低”改为琥珀色、批量添加按钮恢复横向文字。
- **登录弹窗**：展示演示账号，降低体验门槛。

## 五、项目结构整理

```
NutriAI/
├── start.bat / start.ps1 / start-all.bat / stop.bat
├── README.md                    新的使用文档
├── PROJECT_NOTES.md             历史周报（保留）
├── docs/
│   ├── OPTIMIZATION_LOG.md      本文件
│   ├── screenshots/             最终界面截图（浅色/深色/移动端）
│   └── reports/                 第 1-7 周 PPT 与草稿
├── backend/                     源码与构建产物
└── scrap/                       历史临时文件（gitignore）
```

## 六、已知限制

- 邮箱验证流程已打通（注册、验证、重置页面），但未强制“必须验证邮箱才能登录”，避免未配置 SMTP 时无法使用。
- 会话认证采用 `SameSite=Lax` + JSON API；如部署为多域名前后端，请配置 `CORS_ORIGINS` 与 HTTPS/`SESSION_COOKIE_SECURE`。
- 未配置 `AI_API_KEY` 时为离线演示模式：推荐/教练为规则+模板生成，自由问答仅返回引导话术。
- 意图识别仍以规则为主，极端口语可能落到 AI 兜底；新增用例已固化在 `AssistantIntentServiceTest`。

---

## 补充更新：2026-11-11 稳健性修复与打包加固

> 验证：`mvn test` 34/34 通过；`mvn clean package` 生成 57MB Fat JAR；服务启动后 `GET /` 返回 200。

### 一、意图识别稳健性

1. **序数指代餐别不再写死**：`AssistantIntentService` 新增 `mealTypeForHour(int hour)`（5-10 早餐 / 10-14 午餐 / 14-17 加餐 / 17-22 晚餐 / 其余加餐），"第 N 个 / 最后一个"这类序数指代推荐菜时按当前时段推断餐别，替代硬编码"午餐"。
2. **序号关键词补齐**：`extractRecipeIndex` 关键词表新增"最后那道"，覆盖常见口语。
3. **食物识别兜底**：序数指代直接映射前端传入的 `suggestedRecipes` 列表，绕过 AI 食物识别，避免"未知食物 0kcal"。

### 二、落库稳健性

4. **amountGram 空值兜底**：`FoodDiaryService` 新增记录分支将 `amountGram == null` 落库为 0.0；更新分支保留显式传 null 的修改语义。

### 三、单元测试

5. **核心逻辑回归保障**：`AssistantIntentServiceTest` 由 31 条扩展至 34 条，新增序号解析、序号守卫、时段餐别映射三类用例，全量通过。

### 四、构建与部署加固

6. **Fat JAR 缺失修复**：`pom.xml` 的 `spring-boot-maven-plugin` 补 `<executions>` 绑定 `repackage` goal。此前 package 产出 380KB 瘦 jar（无 BOOT-INF/lib、无 Spring Boot loader），`java -jar` 无法启动；修复后生成 57MB Fat JAR。
7. **旧进程占用清理**：残留 Java 进程长期占用 `nutri-ai-0.1.0.jar` 文件句柄，导致 `mvn clean` 报 "Failed to delete"；停止旧进程后构建正常。

### 五、UI 方向

8. **保持助手原生风格**：原计划对 UI 文案做"去 AI 味"改写，试改一版后用户反馈"改后的文本也不太对劲"，最终决定保持原有助手语气，仅做稳健性修复，不调整文案。