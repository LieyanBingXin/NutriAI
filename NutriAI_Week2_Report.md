# NutriAI 第二周汇报

## 本周做了什么

在第一周 AI 推荐的基础上，加上了用户登录系统。

---

## 加了哪些功能

| 功能 | 说明 |
|------|------|
| 注册 / 登录 / 登出 | 用户名 + 密码，密码用 BCrypt 加密 |
| 记住登录状态 | 用 Session Cookie，关浏览器前一直登录着 |
| 保存用户画像 | 身高、体重、年龄、喜好、忌讳等存数据库 |
| 推荐历史记录 | 登录后生成的 AI 推荐自动保存，可查可删 |
| 游客也能用 | 没注册照样能生成推荐，只是不会保存历史 |

---

## 新增了什么技术

- **Spring Security** —— 管登录认证、放行哪些 URL
- **Spring Data JPA** —— 用 Java 类操作数据库表，不用写 SQL
- **H2 数据库** —— 内存数据库，开发用，重启会清空（以后换 MySQL）
- **BCrypt** —— 密码加密，数据库里不存明文

---

## 数据库加了三张表

```
users                ← 用户账号（用户名、加密密码、邮箱）
  │
  ├─ user_profiles    ← 用户画像（身高体重、喜好、忌讳）
  │
  └─ recommendation_records  ← 推荐历史（AI 推荐结果快照）
```

---

## API 接口（新增）

| 方法 | 路径 | 谁能用 |
|------|------|--------|
| POST | `/api/auth/register` | 所有人 |
| POST | `/api/auth/login` | 所有人 |
| POST | `/api/auth/logout` | 登录的人 |
| GET  | `/api/auth/me` | 所有人（检查是否已登录） |
| GET  | `/api/user/profile` | 登录的人 |
| PUT  | `/api/user/profile` | 登录的人 |
| GET  | `/api/recommend/history` | 登录的人 |
| DELETE | `/api/recommend/history/{id}` | 登录的人 |

---

## 遇到的坑（3 个主要的）

**1. 点击注册按钮完全没反应**
- 原因：Spring Security 只放行了 `/api/**`，连首页 `/index.html` 都被拦截了
- 修：在 `SecurityConfig` 里把 `/`, `/css/**`, `/js/**` 也加到放行列表

**2. 前端登录弹窗不显示**
- 原因：新版前端写在 `frontend/` 目录，但后端 `resources/static/` 里还是旧版
- 修：每次启动时自动 `copy frontend/* → backend/resources/static/`

**3. 登录后刷新页面就退出了**
- 原因：Spring Security 的 Session 创建策略没配对
- 修：改成 `SessionCreationPolicy.ALWAYS` + 手动保存 SecurityContext

---

## 运行效果

打开 http://localhost:8080

1. 点右上角"注册" → 填用户名密码 → 注册成功
2. 登录后可以"保存画像"
3. 生成 AI 推荐 → 自动保存到"历史记录"
4. 没登录也能生成推荐，只是不保存

---

## 下周可以做

- 换 MySQL 持久化数据（H2 重启清空）
- 体重变化图表
- AI 流式输出（打字机效果）
- 密码找回

---

## 一键启动

双击 `start.bat` 就行。自动打包 + 启动后端 + 打开浏览器。