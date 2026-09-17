from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE

prs = Presentation()
prs.slide_width = Inches(13.333)
prs.slide_height = Inches(7.5)

COLOR_PRIMARY = RGBColor(0x2E, 0x7D, 0x32)
COLOR_SECONDARY = RGBColor(0x66, 0xBB, 0x6A)
COLOR_DARK = RGBColor(0x1B, 0x5E, 0x20)
COLOR_BG_LIGHT = RGBColor(0xF1, 0xF8, 0xE9)
COLOR_WHITE = RGBColor(0xFF, 0xFF, 0xFF)
COLOR_TEXT = RGBColor(0x33, 0x33, 0x33)
COLOR_GRAY = RGBColor(0x66, 0x66, 0x66)
COLOR_ACCENT = RGBColor(0xFF, 0x6F, 0x00)

def add_background(slide, color):
    background = slide.background
    fill = background.fill
    fill.solid()
    fill.fore_color.rgb = color

def add_shape(slide, left, top, width, height, fill_color, line_color=None):
    shape = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, left, top, width, height)
    shape.fill.solid()
    shape.fill.fore_color.rgb = fill_color
    if line_color:
        shape.line.color.rgb = line_color
    else:
        shape.line.fill.background()
    return shape

def add_text_box(slide, left, top, width, height, text, font_size=18, bold=False, color=COLOR_TEXT, alignment=PP_ALIGN.LEFT, font_name='微软雅黑'):
    txBox = slide.shapes.add_textbox(left, top, width, height)
    tf = txBox.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.alignment = alignment
    run = p.add_run()
    run.text = text
    run.font.size = Pt(font_size)
    run.font.bold = bold
    run.font.color.rgb = color
    run.font.name = font_name
    return txBox

def add_bullet_text(slide, left, top, width, height, items, font_size=16, color=COLOR_TEXT, bold_first=False):
    txBox = slide.shapes.add_textbox(left, top, width, height)
    tf = txBox.text_frame
    tf.word_wrap = True
    for i, item in enumerate(items):
        if i == 0:
            p = tf.paragraphs[0]
        else:
            p = tf.add_paragraph()
        p.space_after = Pt(8)
        run = p.add_run()
        run.text = item
        run.font.size = Pt(font_size)
        run.font.color.rgb = color
        run.font.name = '微软雅黑'
        if bold_first and i == 0:
            run.font.bold = True
    return txBox

def add_slide_title(slide, title, subtitle=None):
    add_background(slide, COLOR_WHITE)
    add_shape(slide, Inches(0), Inches(0), prs.slide_width, Inches(0.15), COLOR_PRIMARY)
    add_shape(slide, Inches(0), Inches(0.15), Inches(0.08), Inches(1.2), COLOR_PRIMARY)
    add_text_box(slide, Inches(0.4), Inches(0.25), Inches(12), Inches(0.8), title, font_size=32, bold=True, color=COLOR_DARK)
    if subtitle:
        add_text_box(slide, Inches(0.4), Inches(1.0), Inches(12), Inches(0.5), subtitle, font_size=16, color=COLOR_GRAY)

def add_section_number(slide, num):
    add_shape(slide, Inches(12.3), Inches(7.0), Inches(0.8), Inches(0.4), COLOR_BG_LIGHT)
    add_text_box(slide, Inches(12.3), Inches(7.05), Inches(0.8), Inches(0.4), str(num), font_size=14, color=COLOR_PRIMARY, alignment=PP_ALIGN.CENTER)

def add_table(slide, left, top, width, height, headers, rows):
    table_shape = slide.shapes.add_table(len(rows) + 1, len(headers), left, top, width, height)
    table = table_shape.table
    for i, header in enumerate(headers):
        cell = table.cell(0, i)
        cell.text = header
        cell.fill.solid()
        cell.fill.fore_color.rgb = COLOR_PRIMARY
        for paragraph in cell.text_frame.paragraphs:
            paragraph.alignment = PP_ALIGN.CENTER
            for run in paragraph.runs:
                run.font.size = Pt(14)
                run.font.bold = True
                run.font.color.rgb = COLOR_WHITE
                run.font.name = '微软雅黑'
    for r, row in enumerate(rows):
        for c, val in enumerate(row):
            cell = table.cell(r + 1, c)
            cell.text = str(val)
            if r % 2 == 0:
                cell.fill.solid()
                cell.fill.fore_color.rgb = COLOR_BG_LIGHT
            for paragraph in cell.text_frame.paragraphs:
                for run in paragraph.runs:
                    run.font.size = Pt(13)
                    run.font.color.rgb = COLOR_TEXT
                    run.font.name = '微软雅黑'
    return table

# ==================== Slide 1: 封面 ====================
slide = prs.slides.add_slide(prs.slide_layouts[6])
add_background(slide, COLOR_PRIMARY)
add_shape(slide, Inches(0), Inches(0), prs.slide_width, Inches(2.5), COLOR_DARK)
add_shape(slide, Inches(0), Inches(5.5), prs.slide_width, Inches(2.0), COLOR_DARK)
add_shape(slide, Inches(1), Inches(2.5), Inches(2), Inches(0.06), COLOR_SECONDARY)
add_shape(slide, Inches(1), Inches(5.3), Inches(11.3), Inches(0.04), COLOR_SECONDARY)

add_text_box(slide, Inches(1), Inches(0.6), Inches(11), Inches(1.0), 'NutriAI', font_size=60, bold=True, color=COLOR_WHITE)
add_text_box(slide, Inches(1), Inches(1.5), Inches(11), Inches(0.8), 'AI 驱动的个性化营养推荐系统', font_size=28, color=COLOR_WHITE)

add_text_box(slide, Inches(1), Inches(3.0), Inches(11), Inches(0.6), 'Java Web 程序设计 · 第一周汇报', font_size=24, color=COLOR_BG_LIGHT)
add_text_box(slide, Inches(1), Inches(3.8), Inches(11), Inches(0.5), '从身体数据到智能饮食建议', font_size=18, color=RGBColor(0xC8, 0xE6, 0xC9))

add_text_box(slide, Inches(1), Inches(6.0), Inches(11), Inches(0.4), '汇报人：________    |    学号：________', font_size=18, color=COLOR_WHITE)
add_text_box(slide, Inches(1), Inches(6.5), Inches(11), Inches(0.4), '2026 年 9 月', font_size=16, color=RGBColor(0xC8, 0xE6, 0xC9))

# ==================== Slide 2: 目录 ====================
slide = prs.slides.add_slide(prs.slide_layouts[6])
add_slide_title(slide, '目录', 'Agenda')
add_section_number(slide, 2)

items = [
    '01    选题背景与意义',
    '02    需求分析',
    '03    技术选型',
    '04    项目架构设计',
    '05    本周已完成工作',
    '06    遇到的问题与解决',
    '07    下周计划',
    '08    项目演示',
]
for i, item in enumerate(items):
    y = Inches(1.8) + Inches(0.65) * i
    color = COLOR_PRIMARY if i < 4 else COLOR_SECONDARY
    add_shape(slide, Inches(1.5), y, Inches(0.12), Inches(0.5), color)
    add_text_box(slide, Inches(1.9), y, Inches(10), Inches(0.5), item, font_size=20, color=COLOR_TEXT)

# ==================== Slide 3: 选题背景 ====================
slide = prs.slides.add_slide(prs.slide_layouts[6])
add_slide_title(slide, '一、选题背景与意义', '为什么做这个项目？')
add_section_number(slide, 3)

add_shape(slide, Inches(0.5), Inches(1.5), Inches(4.0), Inches(2.5), COLOR_BG_LIGHT, COLOR_SECONDARY)
add_text_box(slide, Inches(0.7), Inches(1.7), Inches(3.6), Inches(0.5), '🎯 现实痛点', font_size=20, bold=True, color=COLOR_DARK)
add_bullet_text(slide, Inches(0.7), Inches(2.3), Inches(3.6), Inches(1.6), [
    '• 人们越来越关注健康饮食',
    '• 专业营养师资源有限',
    '• 线下咨询成本高、门槛高',
], font_size=14, color=COLOR_TEXT)

add_shape(slide, Inches(4.7), Inches(1.5), Inches(4.0), Inches(2.5), COLOR_BG_LIGHT, COLOR_SECONDARY)
add_text_box(slide, Inches(4.9), Inches(1.7), Inches(3.6), Inches(0.5), '🤖 AI 能力', font_size=20, bold=True, color=COLOR_DARK)
add_bullet_text(slide, Inches(4.9), Inches(2.3), Inches(3.6), Inches(1.6), [
    '• 大模型具备强自然语言理解',
    '• 能整合营养学知识',
    '• 可作为"AI 营养师"',
], font_size=14, color=COLOR_TEXT)

add_shape(slide, Inches(8.9), Inches(1.5), Inches(4.0), Inches(2.5), COLOR_BG_LIGHT, COLOR_SECONDARY)
add_text_box(slide, Inches(9.1), Inches(1.7), Inches(3.6), Inches(0.5), '✅ 选题价值', font_size=20, bold=True, color=COLOR_DARK)
add_bullet_text(slide, Inches(9.1), Inches(2.3), Inches(3.6), Inches(1.6), [
    '• 紧扣 Java Web + AI 应用',
    '• 满足课程技术要求',
    '• 有实际应用场景',
], font_size=14, color=COLOR_TEXT)

add_shape(slide, Inches(0.5), Inches(4.3), Inches(12.3), Inches(1.8), RGBColor(0xFF, 0xF3, 0xE0), COLOR_ACCENT)
add_text_box(slide, Inches(0.7), Inches(4.5), Inches(11.9), Inches(0.5), '💡 目标用户', font_size=18, bold=True, color=COLOR_ACCENT)
add_text_box(slide, Inches(0.7), Inches(5.1), Inches(11.9), Inches(0.8), '有减脂、增肌、健康改善等需求的普通人群 —— 让每个人都能享受专业级的个性化饮食指导', font_size=16, color=COLOR_TEXT)

# ==================== Slide 4: 需求分析 ====================
slide = prs.slides.add_slide(prs.slide_layouts[6])
add_slide_title(slide, '二、需求分析', '核心功能与非功能需求')
add_section_number(slide, 4)

add_text_box(slide, Inches(0.5), Inches(1.5), Inches(6), Inches(0.5), '📌 核心功能', font_size=22, bold=True, color=COLOR_DARK)

func_items = [
    ('用户信息录入', '性别/年龄/身高/体重/活动水平/健康目标/口味偏好/食物喜好忌讳'),
    ('AI 营养分析', '计算每日所需热量 + 宏量营养素（蛋白质/碳水/脂肪）'),
    ('智能食谱推荐', '个性化推荐 3 道食谱（食材/步骤/营养/耗时/难度）'),
    ('饮食建议', '生成通用健康饮食小贴士'),
]
for i, (title, desc) in enumerate(func_items):
    y = Inches(2.1) + Inches(1.15) * i
    add_shape(slide, Inches(0.5), y, Inches(6.0), Inches(1.0), COLOR_BG_LIGHT, COLOR_SECONDARY)
    add_text_box(slide, Inches(0.7), y + Inches(0.05), Inches(5.6), Inches(0.4), f'{i+1}.  {title}', font_size=16, bold=True, color=COLOR_PRIMARY)
    add_text_box(slide, Inches(0.7), y + Inches(0.45), Inches(5.6), Inches(0.5), desc, font_size=13, color=COLOR_GRAY)

add_text_box(slide, Inches(7.0), Inches(1.5), Inches(6), Inches(0.5), '⚙️ 非功能需求', font_size=22, bold=True, color=COLOR_DARK)

nf_items = [
    '⚡ 性能：AI 响应时间 < 10 秒',
    '🔌 可扩展：方便接入更多 AI 服务商',
    '🛡️ 容错性：AI 不可用时自动降级',
    '📱 兼容性：支持主流浏览器访问',
]
for i, item in enumerate(nf_items):
    y = Inches(2.1) + Inches(0.9) * i
    add_shape(slide, Inches(7.0), y, Inches(5.8), Inches(0.75), RGBColor(0xFF, 0xF8, 0xE1), COLOR_ACCENT)
    add_text_box(slide, Inches(7.2), y + Inches(0.15), Inches(5.4), Inches(0.5), item, font_size=15, color=COLOR_TEXT)

# ==================== Slide 5: 技术选型 ====================
slide = prs.slides.add_slide(prs.slide_layouts[6])
add_slide_title(slide, '三、技术选型', 'Tech Stack')
add_section_number(slide, 5)

add_table(slide, Inches(0.5), Inches(1.5), Inches(12.3), Inches(5.5),
    ['分类', '技术', '选型理由'],
    [
        ['后端框架', 'Spring Boot 3.2.0', '课程主流技术栈，生态成熟，社区活跃'],
        ['编程语言', 'Java 17 / 21', 'LTS 版本，性能稳定，支持新语法'],
        ['AI 调用', 'Spring WebFlux WebClient', '非阻塞 HTTP 客户端，Spring 官方推荐'],
        ['JSON 处理', 'Jackson', 'Spring Boot 默认集成，功能强大'],
        ['数据库', 'H2（内存）+ JPA', '开发阶段快速启动，可无缝切换 MySQL'],
        ['工具库', 'Lombok', '减少样板代码，提升开发效率'],
        ['AI 大模型', 'DeepSeek', '国产模型，性价比高，兼容 OpenAI'],
        ['前端', '原生 HTML/CSS/JS', '本周聚焦后端，快速验证原型'],
    ])

# ==================== Slide 6: 架构设计 ====================
slide = prs.slides.add_slide(prs.slide_layouts[6])
add_slide_title(slide, '四、项目架构设计', 'Architecture Overview')
add_section_number(slide, 6)

y = Inches(1.6)
add_shape(slide, Inches(2), y, Inches(9), Inches(0.7), COLOR_SECONDARY)
add_text_box(slide, Inches(2), y + Inches(0.15), Inches(9), Inches(0.5), '前端原型 (HTML/JS)   http://localhost:3000', font_size=16, bold=True, color=COLOR_WHITE, alignment=PP_ALIGN.CENTER)

add_text_box(slide, Inches(5.5), y + Inches(0.8), Inches(2.3), Inches(0.4), '↓ REST API (JSON)', font_size=12, color=COLOR_GRAY, alignment=PP_ALIGN.CENTER)

y = Inches(2.8)
add_shape(slide, Inches(1), y, Inches(11), Inches(3.0), COLOR_BG_LIGHT, COLOR_SECONDARY)
add_text_box(slide, Inches(1), y + Inches(0.1), Inches(11), Inches(0.4), 'Spring Boot 后端 (:8080)', font_size=16, bold=True, color=COLOR_DARK, alignment=PP_ALIGN.CENTER)

inner_y = y + Inches(0.6)
add_shape(slide, Inches(1.3), inner_y, Inches(3.2), Inches(0.65), COLOR_PRIMARY)
add_text_box(slide, Inches(1.3), inner_y + Inches(0.1), Inches(3.2), Inches(0.5), 'NutritionController', font_size=13, bold=True, color=COLOR_WHITE, alignment=PP_ALIGN.CENTER)

add_text_box(slide, Inches(2.6), inner_y + Inches(0.7), Inches(0.6), Inches(0.3), '↓', font_size=18, color=COLOR_DARK, alignment=PP_ALIGN.CENTER)

inner_y2 = inner_y + Inches(1.0)
add_shape(slide, Inches(1.3), inner_y2, Inches(3.2), Inches(0.65), COLOR_PRIMARY)
add_text_box(slide, Inches(1.3), inner_y2 + Inches(0.1), Inches(3.2), Inches(0.5), 'NutritionService', font_size=13, bold=True, color=COLOR_WHITE, alignment=PP_ALIGN.CENTER)

add_text_box(slide, Inches(2.6), inner_y2 + Inches(0.7), Inches(0.6), Inches(0.3), '↓', font_size=18, color=COLOR_DARK, alignment=PP_ALIGN.CENTER)

inner_y3 = inner_y2 + Inches(1.0)
add_shape(slide, Inches(1.3), inner_y3, Inches(3.2), Inches(0.65), COLOR_ACCENT)
add_text_box(slide, Inches(1.3), inner_y3 + Inches(0.1), Inches(3.2), Inches(0.5), 'AiServiceFactory ★', font_size=13, bold=True, color=COLOR_WHITE, alignment=PP_ALIGN.CENTER)

branch_y = inner_y3 + Inches(0.8)
add_text_box(slide, Inches(1.5), branch_y, Inches(1), Inches(0.3), '↙', font_size=20, color=COLOR_DARK, alignment=PP_ALIGN.CENTER)
add_text_box(slide, Inches(3.5), branch_y, Inches(1), Inches(0.3), '↘', font_size=20, color=COLOR_DARK, alignment=PP_ALIGN.CENTER)

add_shape(slide, Inches(0.8), branch_y + Inches(0.3), Inches(3.0), Inches(0.55), RGBColor(0x15, 0x65, 0xC0))
add_text_box(slide, Inches(0.8), branch_y + Inches(0.35), Inches(3.0), Inches(0.5), 'OpenAiCompat (DeepSeek)', font_size=11, bold=True, color=COLOR_WHITE, alignment=PP_ALIGN.CENTER)

add_shape(slide, Inches(3.8), branch_y + Inches(0.3), Inches(3.0), Inches(0.55), RGBColor(0x7B, 0x1F, 0xA2))
add_text_box(slide, Inches(3.8), branch_y + Inches(0.35), Inches(3.0), Inches(0.5), 'MockAiService (降级)', font_size=11, bold=True, color=COLOR_WHITE, alignment=PP_ALIGN.CENTER)

add_text_box(slide, Inches(7.5), y + Inches(0.6), Inches(4), Inches(0.4), '⭐ AI 抽象层设计亮点', font_size=18, bold=True, color=COLOR_ACCENT)
highlights = [
    '✅ 策略模式 + 工厂模式',
    '✅ 自动扫描注册 AiService',
    '✅ 配置驱动动态路由',
    '✅ 内置容错降级机制',
    '✅ 新增 Provider 零配置',
]
for i, h in enumerate(highlights):
    add_text_box(slide, Inches(7.5), y + Inches(1.1) + Inches(0.45) * i, Inches(4.5), Inches(0.4), h, font_size=14, color=COLOR_TEXT)

add_text_box(slide, Inches(5.5), Inches(5.0), Inches(2.3), Inches(0.4), '↓', font_size=18, color=COLOR_DARK, alignment=PP_ALIGN.CENTER)
add_shape(slide, Inches(2), Inches(5.4), Inches(9), Inches(0.7), COLOR_GRAY)
add_text_box(slide, Inches(2), Inches(5.55), Inches(9), Inches(0.5), 'H2 内存数据库 (预留)', font_size=16, color=COLOR_WHITE, alignment=PP_ALIGN.CENTER)

# ==================== Slide 7: 核心设计 - AI抽象层 ====================
slide = prs.slides.add_slide(prs.slide_layouts[6])
add_slide_title(slide, '四、核心设计：AI 抽象层', '策略模式 + 工厂模式')
add_section_number(slide, 7)

add_shape(slide, Inches(0.5), Inches(1.5), Inches(6.0), Inches(5.5), COLOR_BG_LIGHT, COLOR_SECONDARY)
add_text_box(slide, Inches(0.7), Inches(1.7), Inches(5.6), Inches(0.5), '📦 代码结构', font_size=20, bold=True, color=COLOR_DARK)

code_text = """ai/
├── AiService.java              ← 接口
│   ├── analyzeAndRecommend()
│   ├── getProviderName()
│   └── isAvailable()
├── AiServiceFactory.java       ← 工厂
│   ├── @PostConstruct 自动注册
│   ├── getService() 动态路由
│   └── 失败自动降级 mock
├── MockAiService.java          ← Mock 兜底
└── OpenAiCompatAiService.java  ← DeepSeek 实现"""

txBox = slide.shapes.add_textbox(Inches(0.7), Inches(2.3), Inches(5.6), Inches(4.5))
tf = txBox.text_frame
tf.word_wrap = True
p = tf.paragraphs[0]
run = p.add_run()
run.text = code_text
run.font.size = Pt(13)
run.font.color.rgb = COLOR_TEXT
run.font.name = 'Consolas'

add_shape(slide, Inches(7.0), Inches(1.5), Inches(5.8), Inches(2.5), RGBColor(0xFF, 0xF8, 0xE1), COLOR_ACCENT)
add_text_box(slide, Inches(7.2), Inches(1.7), Inches(5.4), Inches(0.5), '🔧 扩展方式', font_size=18, bold=True, color=COLOR_ACCENT)
add_text_box(slide, Inches(7.2), Inches(2.3), Inches(5.4), Inches(1.6), '新增 AI 服务商（如通义千问、Ollama、Claude）\n\n只需：\n1. 实现 AiService 接口\n2. 加 @Component 注解\n\n→ 零配置侵入！', font_size=14, color=COLOR_TEXT)

add_shape(slide, Inches(7.0), Inches(4.2), Inches(5.8), Inches(2.8), RGBColor(0xE3, 0xF2, 0xFD), RGBColor(0x15, 0x65, 0xC0))
add_text_box(slide, Inches(7.2), Inches(4.4), Inches(5.4), Inches(0.5), '🛡️ 三级降级保护', font_size=18, bold=True, color=RGBColor(0x15, 0x65, 0xC0))
add_text_box(slide, Inches(7.2), Inches(5.0), Inches(5.4), Inches(1.8), 'DeepSeek API 调用\n        ↓ 失败？\nparseResponse 异常 → 返回空 RecommendationResponse\n        ↓ 还不行？\nAiServiceFactory → 自动降级到 MockAiService', font_size=14, color=COLOR_TEXT)

# ==================== Slide 8: 本周完成工作 ====================
slide = prs.slides.add_slide(prs.slide_layouts[6])
add_slide_title(slide, '五、本周已完成工作', 'What We Have Done')
add_section_number(slide, 8)

add_table(slide, Inches(0.5), Inches(1.5), Inches(12.3), Inches(5.5),
    ['序号', '任务', '状态'],
    [
        ['1', '项目脚手架搭建（Maven + Spring Boot 3.2.0）', '✅ 完成'],
        ['2', '数据库设计与 JPA 实体（预留）', '✅ 完成'],
        ['3', 'DTO 数据结构定义（UserProfileRequest / RecommendationResponse）', '✅ 完成'],
        ['4', 'AI 抽象层设计与实现（AiService / Factory / Mock / OpenAiCompat）', '✅ 完成'],
        ['5', 'DeepSeek API 对接与 Prompt 工程', '✅ 完成'],
        ['6', 'REST API 开发（3 个接口）', '✅ 完成'],
        ['7', '统一异常处理 + ApiResponse 响应包装', '✅ 完成'],
        ['8', 'CORS 跨域配置（前后端分离）', '✅ 完成'],
        ['9', '前端原型页面开发（HTML/CSS/JS）', '✅ 完成'],
        ['10', '核心链路联调跑通（前端 → AI → 页面渲染）', '✅ 完成'],
    ])

# ==================== Slide 9: API 接口 ====================
slide = prs.slides.add_slide(prs.slide_layouts[6])
add_slide_title(slide, '五、已实现 API 接口', 'REST Endpoints')
add_section_number(slide, 9)

add_table(slide, Inches(0.5), Inches(1.5), Inches(12.3), Inches(4.2),
    ['方法', '路径', '说明'],
    [
        ['POST', '/api/recommend', '核心接口 — 提交用户信息，AI 返回个性化饮食推荐'],
        ['GET', '/api/ai/providers', '查看当前已注册的 AI 服务商列表'],
        ['GET', '/api/health', '健康检查 — 验证后端服务正常运行'],
    ])

add_text_box(slide, Inches(0.5), Inches(6.0), Inches(12), Inches(0.4), '💡 核心数据流转：UserProfileRequest（输入） → Prompt 构造 → AI API 调用 → JSON 提取解析 → RecommendationResponse（输出）', font_size=14, bold=True, color=COLOR_PRIMARY)

# ==================== Slide 10: 遇到的问题 ====================
slide = prs.slides.add_slide(prs.slide_layouts[6])
add_slide_title(slide, '六、遇到的问题与解决方案', 'Problems & Solutions')
add_section_number(slide, 10)

add_table(slide, Inches(0.5), Inches(1.5), Inches(12.3), Inches(5.5),
    ['问题', '原因', '解决方案'],
    [
        ['Maven 默认用 Java 8 编译失败', '系统 JAVA_HOME 指向 JDK 1.8，Spring Boot 3.x 要求 Java 17+', '手动设置 JAVA_HOME=C:\\Program Files\\Java\\jdk-21.0.9'],
        ['两个 AiService 注册名冲突', 'OpenAiCompat 的 getProviderName() 和 Mock 重名，后者覆盖前者', '固定返回 "openai-compat"，确保每个 provider 名唯一'],
        ['AI 返回带 ```json 代码块', 'LLM 常把 JSON 包在 Markdown 代码块里，直接反序列化报错', '用正则 extractJson() 先提取干净 JSON 再解析'],
        ['DeepSeek 返回 402 Payment Required', '新账户未充值', '充值后恢复正常（最低 1 元即可测试）'],
    ])

# ==================== Slide 11: 下周计划 ====================
slide = prs.slides.add_slide(prs.slide_layouts[6])
add_slide_title(slide, '七、下周计划', 'Next Week Plan')
add_section_number(slide, 11)

tasks = [
    ('🔴 高', '用户历史记录存储', '建 JPA Entity + Repository，每次推荐结果入库'),
    ('🔴 高', '食谱数据库', '预置一批本地食谱作为 AI fallback'),
    ('🟡 中', 'Prompt 优化', '让 AI 输出更精准、更有差异化'),
    ('🟡 中', '前端升级', '引入 Vue 3，改善交互体验'),
    ('🟡 中', '单元测试 + 集成测试', '覆盖 Service 和 Controller 层'),
    ('🟢 低', '更多 AI Provider', '接入通义千问 / 本地 Ollama'),
    ('🟢 低', 'AI 流式响应', 'WebFlux SSE 打字机效果'),
]

for i, (priority, title, desc) in enumerate(tasks):
    col = i % 2
    row = i // 2
    left = Inches(0.5 + col * 6.4)
    top = Inches(1.5 + row * 1.75)
    add_shape(slide, left, top, Inches(6.0), Inches(1.55), COLOR_BG_LIGHT, COLOR_SECONDARY)
    add_text_box(slide, left + Inches(0.2), top + Inches(0.15), Inches(1.2), Inches(0.4), priority, font_size=13, bold=True)
    add_text_box(slide, left + Inches(1.4), top + Inches(0.15), Inches(4.4), Inches(0.4), title, font_size=18, bold=True, color=COLOR_DARK)
    add_text_box(slide, left + Inches(0.2), top + Inches(0.6), Inches(5.6), Inches(0.9), desc, font_size=14, color=COLOR_TEXT)

# ==================== Slide 12: 项目演示 ====================
slide = prs.slides.add_slide(prs.slide_layouts[6])
add_slide_title(slide, '八、项目演示 Demo', 'Live Demo')
add_section_number(slide, 12)

add_shape(slide, Inches(0.5), Inches(1.5), Inches(12.3), Inches(5.5), COLOR_BG_LIGHT, COLOR_SECONDARY)

demo_steps = [
    '🎬 演示步骤：',
    '',
    '1️⃣ 启动后端服务  →  mvn spring-boot:run (端口 8080)',
    '2️⃣ 启动前端页面  →  python -m http.server 3000',
    '3️⃣ 打开浏览器    →  http://localhost:3000',
    '4️⃣ 填写用户信息  →  性别/年龄/身高/体重/活动/目标/口味/喜好/忌讳',
    '5️⃣ 点击「✨ 生成推荐」',
    '6️⃣ 查看 AI 返回结果：',
    '      📋 概要总结',
    '      🥗 营养建议（热量/蛋白质/碳水/脂肪/BMI）',
    '      🍽️ 3 道个性化食谱（食材/步骤/营养/耗时/难度）',
    '      💡 饮食小贴士',
    '',
    '🔄 备选：访问 /api/ai/providers 查看已注册 AI 服务商',
    '',
    '🛡️ 兜底：AI API 不可用时 → 切换 mock 模式继续演示',
]

txBox = slide.shapes.add_textbox(Inches(0.9), Inches(1.7), Inches(11.5), Inches(5.0))
tf = txBox.text_frame
tf.word_wrap = True
for i, step in enumerate(demo_steps):
    if i == 0:
        p = tf.paragraphs[0]
        run = p.add_run()
        run.text = step
        run.font.size = Pt(22)
        run.font.bold = True
        run.font.color.rgb = COLOR_DARK
        run.font.name = '微软雅黑'
    elif step == '':
        tf.add_paragraph().space_after = Pt(4)
    else:
        p = tf.add_paragraph()
        p.space_after = Pt(4)
        run = p.add_run()
        run.text = step
        run.font.size = Pt(15)
        run.font.color.rgb = COLOR_TEXT
        run.font.name = '微软雅黑'

# ==================== Slide 13: 总结 ====================
slide = prs.slides.add_slide(prs.slide_layouts[6])
add_background(slide, COLOR_PRIMARY)

add_text_box(slide, Inches(1), Inches(2.0), Inches(11), Inches(1.0), '谢谢聆听', font_size=72, bold=True, color=COLOR_WHITE, alignment=PP_ALIGN.CENTER)
add_text_box(slide, Inches(1), Inches(3.2), Inches(11), Inches(0.6), 'Thank You', font_size=36, color=COLOR_BG_LIGHT, alignment=PP_ALIGN.CENTER)

add_shape(slide, Inches(5), Inches(4.2), Inches(3.3), Inches(0.04), COLOR_SECONDARY)

add_text_box(slide, Inches(1), Inches(4.8), Inches(11), Inches(0.6), '敬请各位老师批评指正', font_size=24, color=COLOR_WHITE, alignment=PP_ALIGN.CENTER)
add_text_box(slide, Inches(1), Inches(5.6), Inches(11), Inches(0.5), 'Q & A', font_size=20, color=COLOR_BG_LIGHT, alignment=PP_ALIGN.CENTER)

# ==================== Slide 14: 技术栈附录 ====================
slide = prs.slides.add_slide(prs.slide_layouts[6])
add_slide_title(slide, '附录：项目技术栈', 'Tech Stack Details')
add_section_number(slide, 14)

add_text_box(slide, Inches(0.5), Inches(1.5), Inches(6), Inches(0.5), '🔧 运行环境', font_size=20, bold=True, color=COLOR_DARK)
env_items = [
    ['Java', 'JDK 21.0.9'],
    ['Maven', '3.9.9 (D:\\apache-maven-3.9.9)'],
    ['后端端口', '8080'],
    ['前端端口', '3000 (python http.server)'],
    ['数据库', 'H2 内存库（console: /h2-console）'],
    ['AI Provider', 'openai-compat (DeepSeek)'],
]
for i, (k, v) in enumerate(env_items):
    y = Inches(2.1) + Inches(0.6) * i
    add_shape(slide, Inches(0.5), y, Inches(2.0), Inches(0.5), COLOR_PRIMARY)
    add_text_box(slide, Inches(0.5), y + Inches(0.08), Inches(2.0), Inches(0.4), k, font_size=14, bold=True, color=COLOR_WHITE, alignment=PP_ALIGN.CENTER)
    add_shape(slide, Inches(2.5), y, Inches(10.3), Inches(0.5), COLOR_BG_LIGHT, COLOR_SECONDARY)
    add_text_box(slide, Inches(2.7), y + Inches(0.08), Inches(10), Inches(0.4), v, font_size=14, color=COLOR_TEXT)

add_text_box(slide, Inches(0.5), Inches(6.0), Inches(12), Inches(0.4), '💡 切换 Mock 模式：application.yml 中把 provider 改成 mock 即可，不需要 API Key', font_size=14, bold=True, color=COLOR_ACCENT)

# ==================== 保存 ====================
output_path = r'd:\Web_JavaWork\NutriAI\NutriAI_Week1_Report.pptx'
prs.save(output_path)
print(f'PPT 生成成功！\n文件路径: {output_path}')
print(f'\n共生成 {len(prs.slides)} 页幻灯片')