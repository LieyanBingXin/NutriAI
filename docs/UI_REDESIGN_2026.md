# NutriAI 2026 视觉与端侧改版记录

> 本次目标：把原本偏“课程作业”的界面升级为可上线的健康产品视觉；补齐安卓浏览器/PWA 体验；修复可见细节问题；准备 Windows 与 Linux 两套部署。

## 一、设计方向：Editorial Wellness

- **主风格**：浅色纸质编辑风（warm paper / forest green / clay accent），而不是常见的绿色渐变 SaaS。
- **字体**：系统无衬线 + 衬线英文小标题（`Iowan Old Style / Palatino`），中文保持高可读系统字体。
- **色彩 token**：`--paper/--surface/--ink/--forest/--sage/--clay`，统一驱动明暗两套主题。
- **版式**：桌面固定侧边栏 + 页头 + 大留白；移动端底部导航 + 单列内容；卡片改为细边框、低阴影、圆角 24。
- **首屏**：双栏 Hero（文案 + 今日营养玻璃卡 + AI Note 便签），替代原来的居中大标题。
- **图标**：导航使用线性 SVG；保留少量 emoji 作为健康产品的轻量点缀。

## 二、结构与导航

- 新增桌面侧边栏（Workspace / Library 分组、底部用户区、Today's Note）。
- 移动端底部导航保留 5 个主入口：AI 助手、我的看板、体重、食物日记、更多。
- 「更多」为底部抽屉：画像、历史、收藏、食谱本、切换主题、退出。
- 页头随 Tab 切换标题与日期；深色切换按钮全局可用。
- PWA 快捷方式支持 `?tab=assistant|diary|weight` 深链。

## 三、安卓端适配（移动 Web + PWA）

- `viewport-fit=cover` + `env(safe-area-inset-bottom)`，适配刘海屏与手势条。
- 底部导航固定、触控目标 ≥ 34–40px，去除点按高亮与 300ms 延迟。
- 输入框在移动端统一 16px，避免 iOS 聚焦缩放。
- 聊天页使用 `100dvh` 计算高度，键盘弹出时输入框保持在可视区。
- 移动端隐藏桌面便利贴侧栏与冗余快捷面板，优先保证对话和记录路径。
- PWA：`manifest.json`、192/512/maskable 图标、Service Worker 应用壳缓存、离线页、安装提示。
- Service Worker 不缓存 `/api/`，避免隐私数据落盘；静态资源 stale-while-revalidate。

## 四、细节修复

| 问题 | 修复 |
|------|------|
| 看板「所在地区 ????」 | 历史脏数据统一清洗为「未填」，并显示“完善画像”入口 |
| 历史画像字段小写（male/maintain）导致显示“未填” | 渲染前统一 `toUpperCase()` 归一化 |
| 移动端导航与内容重叠、只露出 4 个标签 | 重做底部导航 + 内容安全区 |
| 便利贴/会话标签在手机占满首屏 | 移动端隐藏，保留底部「更多」入口 |
| 体重曲线空白 | 数据窗口 30 → 180 天；无数据时显示空状态；Chart.js 本地化 |
| 登录后首屏 `/proactive-tip` 偶发 401 | 增加登录态与 `res.ok` 判断，登录成功后再拉取 |
| `buildTag` 版本串出现在页脚 | 移除，页脚改为品牌文案 |
| Maven 重复 repackage | 删除多余的 `spring-boot:repackage` execution |
| CDN 不稳定导致图表/Markdown 失效 | Chart.js / marked 放入 `static/vendor/` |

## 五、部署准备

- Windows：计划任务开机自启、SYSTEM 运行、防火墙、日志、mysqldump 备份、卸载脚本。
- Linux：Docker Compose（MySQL + App + Nginx）、健康检查、SSE 免缓冲、登录限流、HTTPS 说明、备份与更新脚本、systemd 备选。
- 应用：`forward-headers-strategy=framework`、生产滚动日志、Cookie Secure、独立 MySQL 账号建议。
- 详见 [deploy/README.md](../deploy/README.md)。

## 六、验证

- `mvn clean package`：BUILD SUCCESS，**68 个测试全部通过**。
- 桌面 1440×1000：首屏 / 助手 / 看板 / 日记 / 体重 / 画像 / 深色。
- 安卓 412×915（DPR2）：助手 / 看板 / 日记，底部导航与输入框均在安全区内。
- PWA 资源：manifest / sw.js / offline.html / icons / vendor 全部 200。

## 七、响应式回归（320–1440px）

- 覆盖 320×780、360×800、390×844、412×915、768×1024、1024×768、1280×800、1440×1000、1920×1080、2560×1440，以及 844×390 横屏。
- 大屏修正：工作区最大宽度 1520px 并在侧栏右侧居中；助手内容列最大 1280px 居中，避免 1920/2560 下所有内容贴左、右侧大片空白。
- 修复：768px 教练面板仍被旧 `grid-template-areas` 强制两列；360/390/412 看板双列数值被挤压；切 Tab 自动聚焦输入框导致页面滚离标题；1280/1024 聊天输入区落到视口外；320px 首屏标题出现“子。”单字行；看板副标题把“目标”拆到两行；收藏页在手机端仍显示整块便利贴侧栏；“更多”抽屉 320px 下底部按钮换行。
- 回归方法：无头 Edge 逐页检测 `scrollWidth > clientWidth`、视口越界、窄容器竖排、`nowrap` 裁切，并逐张人工查看截图；最终全部页面 **0 个溢出/竖排问题**。

