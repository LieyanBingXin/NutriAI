# -*- coding: utf-8 -*-
"""Build NutriAI 第八周汇报 PPT in the same visual language as Week 1-7 decks."""
import os

from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR, MSO_AUTO_SIZE
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.dml import MSO_LINE_DASH_STYLE
from pptx.oxml.ns import qn
from lxml.etree import SubElement

# ---------------------------------------------------------------- design tokens
INK      = RGBColor(0x1F, 0x2A, 0x24)
BODY     = RGBColor(0x4B, 0x57, 0x50)
MUTED    = RGBColor(0x86, 0x91, 0x89)
FAINT    = RGBColor(0xB4, 0xBD, 0xB6)
GREEN    = RGBColor(0x2E, 0x7D, 0x32)
DGREEN   = RGBColor(0x1B, 0x5E, 0x20)
DEEP     = RGBColor(0x12, 0x2B, 0x17)
ORANGE   = RGBColor(0xD9, 0x5B, 0x0E)
BLUE     = RGBColor(0x2F, 0x6F, 0xB2)
PURPLE   = RGBColor(0x7C, 0x4D, 0xBE)
MAGENTA  = RGBColor(0x9B, 0x4D, 0x9A)
RED      = RGBColor(0xB3, 0x3A, 0x2E)
LINE     = RGBColor(0xDD, 0xE3, 0xDD)
LINE2    = RGBColor(0xEC, 0xF0, 0xEC)
CARD     = RGBColor(0xF6, 0xF8, 0xF5)
CARD2    = RGBColor(0xFA, 0xFB, 0xF9)
WHITE    = RGBColor(0xFF, 0xFF, 0xFF)

# board category colours (per draft appendix)
C_BASE    = RGBColor(0x10, 0xB9, 0x81)
C_STATUS  = RGBColor(0xEF, 0x44, 0x44)
C_LIKE    = RGBColor(0x10, 0xB9, 0x81)
C_DISLIKE = RGBColor(0x6B, 0x72, 0x80)
C_FRIDGE  = RGBColor(0x3B, 0x82, 0xF6)
C_PERSONA = RGBColor(0x8B, 0x5C, 0xF6)
C_GAP     = RGBColor(0xC0, 0x84, 0xFC)

YH  = "Microsoft YaHei"
MONO = "Consolas"

SW, SH = 13.333, 7.5
L, R = 0.95, 12.38
CW = R - L
TOTAL = 15

prs = Presentation()
prs.slide_width = Inches(SW)
prs.slide_height = Inches(SH)
BLANK = prs.slide_layouts[6]


# ---------------------------------------------------------------- primitives
def new_slide(bg=None):
    s = prs.slides.add_slide(BLANK)
    if bg is not None:
        s.background.fill.solid()
        s.background.fill.fore_color.rgb = bg
    return s


def _style_run(r, size, bold, color, font, italic=False, spc=None):
    f = r.font
    f.size = Pt(size)
    f.bold = bold
    f.italic = italic
    f.color.rgb = color
    f.name = font
    rPr = r._r.get_or_add_rPr()
    ea = rPr.find(qn("a:ea"))
    if ea is None:
        ea = SubElement(rPr, qn("a:ea"))
    ea.set("typeface", font)
    if spc is not None:
        rPr.set("spc", str(int(spc * 100)))


def tbox(slide, x, y, w, h, anchor=MSO_ANCHOR.TOP):
    tb = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = tb.text_frame
    tf.word_wrap = True
    tf.auto_size = MSO_AUTO_SIZE.NONE
    tf.margin_left = 0
    tf.margin_right = 0
    tf.margin_top = 0
    tf.margin_bottom = 0
    tf.vertical_anchor = anchor
    return tf


def text(slide, x, y, w, h, s, size=10, bold=False, color=BODY, font=YH,
         align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP, spacing=1.25, italic=False):
    tf = tbox(slide, x, y, w, h, anchor)
    for i, line in enumerate(s.split("\n")):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = align
        p.line_spacing = spacing
        r = p.add_run()
        r.text = line
        _style_run(r, size, bold, color, font, italic)
    return tf


def rich(slide, x, y, w, h, paras, anchor=MSO_ANCHOR.TOP):
    """paras: list of dict(runs=[(text, kwargs)], align=, spacing=, before=, after=)"""
    tf = tbox(slide, x, y, w, h, anchor)
    for i, spec in enumerate(paras):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = spec.get("align", PP_ALIGN.LEFT)
        p.line_spacing = spec.get("spacing", 1.25)
        if spec.get("before"):
            p.space_before = Pt(spec["before"])
        if spec.get("after") is not None:
            p.space_after = Pt(spec["after"])
        for t, kw in spec["runs"]:
            r = p.add_run()
            r.text = t
            _style_run(r, kw.get("size", 10), kw.get("bold", False),
                       kw.get("color", BODY), kw.get("font", YH),
                       kw.get("italic", False))
    return tf


def box(slide, x, y, w, h, fill=None, line=None, lw=0.9, dash=None,
        radius=None, shape=MSO_SHAPE.RECTANGLE):
    shp = slide.shapes.add_shape(shape, Inches(x), Inches(y), Inches(w), Inches(h))
    shp.shadow.inherit = False
    # drop the theme style reference so no inherited shadow / outline renders
    style = shp._element.find(qn("p:style"))
    if style is not None:
        shp._element.remove(style)
    if fill is None:
        shp.fill.background()
    else:
        shp.fill.solid()
        shp.fill.fore_color.rgb = fill
    if line is None:
        shp.line.fill.background()
    else:
        shp.line.color.rgb = line
        shp.line.width = Pt(lw)
        if dash is not None:
            shp.line.dash_style = dash
    if radius is not None and shape == MSO_SHAPE.ROUNDED_RECTANGLE:
        shp.adjustments[0] = radius
    shp.text_frame.word_wrap = True
    return shp


def hline(slide, x, y, w, color=LINE):
    return box(slide, x, y, w, 0.01, fill=color)


def gradient_bar(slide, x, y, w, h, c1, c2):
    shp = box(slide, x, y, w, h, fill=c1)
    try:
        shp.fill.gradient()
        shp.fill.gradient_angle = 0.0
        stops = shp.fill.gradient_stops
        stops[0].color.rgb = c1
        stops[0].position = 0.0
        stops[1].color.rgb = c2
        stops[1].position = 1.0
    except Exception:
        shp.fill.solid()
        shp.fill.fore_color.rgb = c1
    return shp


def header(slide, title, page, lead=None, lead_right=None):
    text(slide, L, 0.62, 11.20, 0.62, title, 27, True, INK, spacing=1.0)
    box(slide, L, 1.44, 0.85, 0.012, fill=GREEN)
    hline(slide, 1.90, 1.44, R - 1.90, LINE)
    if lead:
        text(slide, L, 1.60, 9.20, 0.28, lead, 11, False, BODY, spacing=1.15)
    if lead_right:
        text(slide, 9.90, 1.60, 2.48, 0.28, lead_right, 9.0, False, MUTED,
             align=PP_ALIGN.RIGHT, spacing=1.15)
    footer(slide, page)


def footer(slide, page):
    hline(slide, L, 7.02, CW, LINE2)
    text(slide, L, 7.08, 7.00, 0.26, "NutriAI  ·  第八周技术汇报", 8.5, False, MUTED,
         spacing=1.0)
    text(slide, 10.98, 7.08, 1.40, 0.26, f"{page:02d} / {TOTAL}", 8.5, False, MUTED,
         align=PP_ALIGN.RIGHT, spacing=1.0)


def eyebrow(slide, x, y, s, color=GREEN, w=5.2, size=9.5):
    text(slide, x, y, w, 0.24, s, size, True, color, spacing=1.0)


def chip(slide, x, y, w, h, label, color=None, size=8.8, fill=WHITE, line=LINE):
    box(slide, x, y, w, h, fill=fill, line=line, lw=0.9,
        shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.16)
    if color is not None:
        box(slide, x + 0.14, y + h / 2 - 0.045, 0.09, 0.09, fill=color)
        text(slide, x + 0.30, y + (h - 0.20) / 2, w - 0.36, 0.20, label, size,
             False, INK, spacing=1.0)
    else:
        text(slide, x, y + (h - 0.20) / 2, w, 0.20, label, size, False, INK,
             align=PP_ALIGN.CENTER, spacing=1.0)


def card(slide, x, y, w, h, fill=CARD2, line=LINE, accent=None, radius=0.04):
    shp = box(slide, x, y, w, h, fill=fill, line=line, lw=0.9,
              shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=radius)
    if accent is not None:
        box(slide, x, y, 0.04, h, fill=accent)
    return shp


def band(slide, x, y, w, h, fill=CARD):
    return box(slide, x, y, w, h, fill=fill)


# ================================================================ slide 1 cover
s = new_slide(RGBColor(0xFA, 0xFB, 0xF9))
box(s, L, 1.66, 0.05, 2.95, fill=GREEN)
text(s, 1.30, 1.60, 8.0, 0.30, "NutriAI", 15, True, GREEN, spacing=1.0)
text(s, 1.30, 1.94, 10.50, 0.95, "智能营养管家", 46, True, INK, spacing=1.0)
text(s, 1.30, 3.02, 10.50, 0.40,
     "AI 对话助手  ·  便利贴看板  ·  信息标签化  ·  本地 RAG", 16, False, BODY,
     spacing=1.0)
hline(s, 1.30, 3.74, 2.30, LINE)
text(s, 1.30, 3.92, 9.00, 0.30, "2026-11-11     ·     第八周技术汇报", 11.5, False,
     BODY, spacing=1.0)
text(s, 1.30, 4.26, 10.20, 0.30,
     "一句话看懂你的用户 —— 把信息变成标签，让 AI 真正“读懂”你", 10.5, False,
     MUTED, spacing=1.0)
text(s, 1.30, 5.28, 9.50, 0.30,
     "Spring Boot   ·   原生前端   ·   本地 RAG / AI 助手", 9.5, False, FAINT,
     spacing=1.0)
hline(s, L, 7.02, CW, LINE2)
text(s, L, 7.08, 7.00, 0.26, "NutriAI  ·  Week 8", 8.5, False, MUTED, spacing=1.0)
text(s, 10.98, 7.08, 1.40, 0.26, f"01 / {TOTAL}", 8.5, False, MUTED,
     align=PP_ALIGN.RIGHT, spacing=1.0)

# ================================================================ slide 2 总览
s = new_slide(WHITE)
header(s, "一条主线：信息标签化", 2)

eyebrow(s, L, 1.62, "本周主线")
text(s, L, 1.86, 5.30, 0.62, "散落的信息，怎么让 AI 一次读全？", 20, True, INK,
     spacing=1.15)
text(s, L, 2.60, 5.30, 0.62,
     "喜好、忌口、冰箱、身体状态、角色偏好——过去散落在表单、备注和记忆里。",
     10.5, False, BODY, spacing=1.35)

steps = [("散落", "F4F7F4", MUTED), ("统一", "EAF1EA", BODY),
         ("可管理", "DFEBDF", INK), ("可引用", "D2E3D2", DGREEN)]
xs = [L, 2.29, 3.63, 4.97]
for (label, fill, col), x in zip(steps, xs):
    box(s, x, 3.40, 1.28, 0.40, fill=RGBColor.from_string(fill))
    text(s, x, 3.50, 1.28, 0.22, label, 9.8, True, col, align=PP_ALIGN.CENTER,
         spacing=1.0)

text(s, L, 3.96, 5.30, 0.45,
     "信息标签化：把每一条信息抽象成「标签」，集中放在看板上，一眼看全、随时引用。",
     9.0, False, MUTED, spacing=1.35)

card(s, L, 4.55, 5.30, 1.28, fill=CARD, line=None, accent=GREEN)
eyebrow(s, 1.20, 4.68, "定义", GREEN, w=2.0, size=8.5)
text(s, 1.20, 4.92, 4.85, 0.55,
     "一条信息 = 一个标签。七类信息分区存放，看板一眼看全。", 11.5, True, INK,
     spacing=1.3)
text(s, 1.20, 5.50, 4.85, 0.24, "AI 每一轮对话都自动读全，不用用户勾选。", 9.3, False,
     MUTED, spacing=1.0)

box(s, 6.45, 1.86, 0.01, 4.02, fill=LINE2)

points = [
    (GREEN, "01", "AI 对话助手", "18 种规则意图 + AI 兜底；命中即执行，零 API 成本。"),
    (BLUE, "02", "本地 RAG", "权威食物库 + 膳食指南，让 AI 有据可依、不编造营养数值。"),
    (PURPLE, "03", "便利贴看板", "七大类信息分区，AI 自动读全，对话中可引用提及。"),
    (ORANGE, "04", "稳健性修复", "意图 / 落库 / 打包 / 测试全链路加固。"),
]
for i, (col, num, name, desc) in enumerate(points):
    y = 1.86 + i * 1.02
    if i:
        hline(s, 6.75, y - 0.10, 5.63, LINE2)
    text(s, 6.75, y + 0.02, 0.45, 0.24, num, 9.0, True, col, font=MONO, spacing=1.0)
    text(s, 7.22, y - 0.02, 5.15, 0.28, name, 12, True, INK, spacing=1.0)
    text(s, 7.22, y + 0.28, 5.16, 0.50, desc, 9.3, False, BODY, spacing=1.3)

band(s, L, 6.12, CW, 0.62)
text(s, 1.20, 6.28, 10.90, 0.30,
     "一句话：不做堆料，做“把信息盘活”这件事。", 11.5, True, DGREEN, spacing=1.0)

# ================================================================ slide 3 概念
s = new_slide(WHITE)
header(s, "什么是「信息标签化」", 3)

text(s, L, 1.60, 0.60, 0.28, "痛点", 9.5, True, ORANGE, spacing=1.15)
text(s, 1.62, 1.60, 10.60, 0.28,
     "信息散落在各自的地方，AI 看不到，只能靠用户一遍遍重复解释。", 11, False,
     BODY, spacing=1.15)

# left panel
card(s, L, 1.98, 4.80, 2.97, fill=CARD2, line=LINE)
text(s, 1.20, 2.14, 3.0, 0.24, "传统做法", 9.5, True, MUTED, spacing=1.0)
rows = [("喜好", "藏在画像表单里"), ("忌口", "记在备注里"),
        ("冰箱", "用户自己心里记着"), ("身体状态", "问一次说一次"),
        ("AI 角色", "每次重新设定")]
for i, (k, v) in enumerate(rows):
    y = 2.46 + i * 0.52
    text(s, 1.20, y, 1.00, 0.26, k, 10, True, INK, spacing=1.0)
    text(s, 2.18, y, 0.25, 0.26, "→", 9.5, False, FAINT, spacing=1.0)
    text(s, 2.48, y, 3.10, 0.26, v, 9.5, False, BODY, spacing=1.0)

# right panel
card(s, 6.05, 1.98, 6.33, 2.97, fill=CARD, line=LINE)
text(s, 6.30, 2.14, 3.0, 0.24, "我们的做法", 9.5, True, DGREEN, spacing=1.0)
rows2 = [(C_LIKE, "喜好", "统一管理，看板一个分区"),
         (C_DISLIKE, "忌口", "自动避开，推荐与记录都不碰"),
         (C_FRIDGE, "冰箱", "有料推荐，现有食材优先"),
         (C_STATUS, "身体状态", "据此调整，营养建议跟着变"),
         (C_PERSONA, "AI 角色", "注入提示，改 AI 的语气与称呼")]
for i, (col, k, v) in enumerate(rows2):
    y = 2.50 + i * 0.48
    box(s, 6.30, y + 0.08, 0.11, 0.11, fill=col)
    text(s, 6.52, y, 1.15, 0.26, k, 10, True, INK, spacing=1.0)
    text(s, 7.72, y, 0.25, 0.26, "→", 9.5, False, FAINT, spacing=1.0)
    text(s, 8.02, y, 4.10, 0.26, v, 9.5, False, BODY, spacing=1.0)

# seven tags strip
text(s, L, 5.15, 5.60, 0.24, "七类信息，七种标签，一个看板", 9.5, True, INK,
     spacing=1.0)
text(s, 6.55, 5.15, 5.83, 0.24, "来源不同：手动维护 / 系统计算 / 只读摘要", 9.0,
     False, MUTED, align=PP_ALIGN.RIGHT, spacing=1.0)
cats = [(C_BASE, "基础信息"), (C_STATUS, "身体状态"), (C_LIKE, "喜欢吃"),
        (C_DISLIKE, "不喜欢 · 过敏"), (C_FRIDGE, "冰箱里有"),
        (C_PERSONA, "AI 角色"), (C_GAP, "AI 营养提醒")]
for i, (col, label) in enumerate(cats):
    chip(s, L + i * 1.65, 5.48, 1.49, 0.42, label, color=col, size=8.6)

hline(s, L, 6.02, CW, LINE2)
text(s, L, 6.14, 5.45, 0.26,
     "·  用户只需「打标签」，不用反复解释自己是谁", 9.8, False, BODY, spacing=1.0)
text(s, 6.60, 6.14, 5.60, 0.26,
     "·  AI 每轮自动带完整上下文，回复更精准", 9.8, False, BODY, spacing=1.0)

# ================================================================ slide 4 18 意图
s = new_slide(WHITE)
header(s, "AI 助手：18 种规则意图 + AI 兜底", 4,
       lead="设计原则：能规则解析的，绝不浪费一次 AI 调用。",
       lead_right="18 种规则  +  1 种兜底")

CW3 = 3.61
GAP3 = (CW - 3 * CW3) / 2
COLX = [L, L + CW3 + GAP3, L + 2 * (CW3 + GAP3)]

groups = [
    (COLX[0], GREEN, "查询类", "6 种 · 只读", [
        ("今日汇总", "今天吃了多少"), ("本周汇总", "这周数据"),
        ("查体重", "现在多重"), ("冰箱查询", "冰箱有什么"),
        ("营养缺口", "缺什么"), ("今日进度", "还能吃多少")], 3.30, 21.0),
    (COLX[1], ORANGE, "写入类", "8 种 · 自动执行", [
        ("记录体重", "今天 68kg"), ("记录饮食", "早餐吃了鸡蛋+牛奶"),
        ("冰箱添加", "我家有鸡胸肉"), ("清空冰箱", "清空冰箱"),
        ("移除食材", "吃完鸡蛋了"), ("撤销上一条", "刚才记错了"),
        ("偏好", "喜欢番茄/讨厌香菜"), ("更新画像", "我要减脂")], 3.30, 7.0),
]
for x, col, name, sub, items, h, gap in groups:
    card(s, x, 2.00, CW3, h, fill=WHITE, line=LINE, accent=None)
    box(s, x, 2.00, CW3, 0.04, fill=col)
    text(s, x + 0.24, 2.20, 2.20, 0.24, name, 10.5, True, col, spacing=1.0)
    text(s, x + 2.00, 2.22, 1.37, 0.22, sub, 8.2, False, MUTED,
         align=PP_ALIGN.RIGHT, spacing=1.0)
    paras = [{"runs": [(k, {"bold": True, "color": INK, "size": 9.5}),
                       ("   " + v, {"color": MUTED, "size": 8.8})],
              "after": gap, "spacing": 1.0} for k, v in items]
    rich(s, x + 0.24, 2.58, CW3 - 0.44, h - 0.70, paras)

# third column: generate card + AI fallback card
x = COLX[2]
card(s, x, 2.00, CW3, 1.52, fill=WHITE, line=LINE)
box(s, x, 2.00, CW3, 0.04, fill=BLUE)
text(s, x + 0.24, 2.18, 2.20, 0.24, "生成 / 行动类", 10.5, True, BLUE, spacing=1.0)
text(s, x + 2.00, 2.20, 1.37, 0.22, "4 种", 8.2, False, MUTED,
     align=PP_ALIGN.RIGHT, spacing=1.0)
paras = [{"runs": [(k, {"bold": True, "color": INK, "size": 9.5}),
                   ("   " + v, {"color": MUTED, "size": 8.8})],
          "after": 7.0, "spacing": 1.0}
         for k, v in [("推荐食谱", "今晚吃什么"), ("换一批", "再推荐一个"),
                      ("一周食谱规划", "给我一周菜单"), ("帮助", "你能做什么")]]
rich(s, x + 0.24, 2.52, CW3 - 0.44, 0.98, paras)

card(s, x, 3.66, CW3, 1.64, fill=RGBColor(0xF9, 0xF6, 0xFC), line=LINE)
box(s, x, 3.66, CW3, 0.04, fill=PURPLE)
text(s, x + 0.24, 3.84, 2.30, 0.24, "AI 兜底", 10.5, True, PURPLE, spacing=1.0)
text(s, x + 2.00, 3.86, 1.37, 0.22, "1 种", 8.2, False, MUTED,
     align=PP_ALIGN.RIGHT, spacing=1.0)
text(s, x + 0.24, 4.20, CW3 - 0.44, 0.86,
     "以上都不匹配 → 拼装完整上下文（画像 + 偏好 + 冰箱 + 今日数据）→ 流式返回。",
     9.0, False, BODY, spacing=1.35)
text(s, x + 0.24, 5.02, CW3 - 0.44, 0.24, "带真实数据回答，不瞎编。", 8.8, True,
     PURPLE, spacing=1.0)

# key design band
band(s, L, 5.60, CW, 0.96)
keys = [("规则命中", "即时完成，零 API 成本"),
        ("规则未命中", "带真实上下文发 AI，不瞎编"),
        ("写操作", "返回确认卡，避免误操作用户数据")]
for i, (k, v) in enumerate(keys):
    x = COLX[i]
    text(s, x, 5.74, 3.30, 0.24, k, 9.5, True, INK, spacing=1.0)
    text(s, x, 5.99, CW3 - 0.20, 0.46, v, 9.0, False, BODY, spacing=1.25)
    if i:
        box(s, x - GAP3 / 2, 5.76, 0.01, 0.62, fill=RGBColor(0xDD, 0xE3, 0xDD))

# ================================================================ slide 5 流水线
s = new_slide(WHITE)
header(s, "三层处理流水线", 5,
       lead="用户说：“今天吃了 200g 牛肉，刚称 68kg”",
       lead_right="规则优先，AI 兜底")

layers = [
    (GREEN, "L0", "预加载真实数据",
     "取今日真实饮食记录 + 用户画像 + 冰箱快照",
     "后续 AI 兜底时用的是真数据，不瞎编", "预加载"),
    (BLUE, "L1", "查类意图（只读，优先级最高）",
     "“查一下”“今天吃了”“这周”“缺什么”",
     "命中即返回汇总 / 查询结果", "只读"),
    (ORANGE, "L2", "写类意图（自动提取 + 执行）",
     "体重三层正则 → “刚称 68kg” ✓　　食材切分 → 鸡蛋、牛奶",
     "调对应 Service 直接写入数据库", "写库"),
    (PURPLE, "L3", "分析 + AI 兜底",
     "“缺什么” → 营养缺口分析　　“推荐” → 生成食谱",
     "其余 → 完整上下文发 AI，流式返回", "分析 / 兜底"),
]
for i, (col, tag, title, l1, l2, right) in enumerate(layers):
    y = 2.05 + i * 1.13
    band(s, L, y, CW, 1.00, fill=RGBColor(0xF7, 0xF9, 0xF7))
    box(s, L, y, 0.04, 1.00, fill=col)
    box(s, 1.23, y + 0.16, 0.60, 0.26, fill=WHITE, line=LINE,
        shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.2)
    text(s, 1.23, y + 0.20, 0.60, 0.20, tag, 8.5, True, BODY, font=MONO,
         align=PP_ALIGN.CENTER, spacing=1.0)
    text(s, 1.97, y + 0.13, 8.30, 0.30, title, 12.5, True, INK, spacing=1.0)
    text(s, 1.97, y + 0.45, 8.55, 0.26, l1, 9.8, False, BODY, spacing=1.0)
    text(s, 1.97, y + 0.72, 8.55, 0.24, l2, 9.8, False, MUTED, spacing=1.0)
    text(s, 10.85, y + 0.16, 1.30, 0.24, right, 9.0, False, MUTED,
         align=PP_ALIGN.RIGHT, spacing=1.0)

text(s, L, 6.62, 10.00, 0.26,
     "顺序有意义：先查后写，只读意图永远优先于写操作；全部未命中才交给 AI。",
     9.5, False, MUTED, spacing=1.0)

# ================================================================ slide 6 RAG
s = new_slide(WHITE)
header(s, "本地 RAG：让 AI 不再“编营养数据”", 6,
       lead="大模型背不出每 100g 鸡胸肉是多少卡；硬答，就会编。")

# left column
eyebrow(s, L, 1.96, "①  检索 · 轻量 TF-IDF（不依赖向量库）")
rich(s, L, 2.28, 5.60, 0.86, [
    {"runs": [("中文分词", {"bold": True, "size": 9.5, "color": INK}),
              ("　按 \\p{L}\\p{N} 切词 + 字符 bigram", {"size": 9.5})],
     "spacing": 1.0, "after": 7},
    {"runs": [("打分", {"bold": True, "size": 9.5, "color": INK}),
              ("　命中词数 / 查询词数", {"size": 9.5})],
     "spacing": 1.0, "after": 7},
    {"runs": [("Top-K", {"bold": True, "size": 9.5, "color": INK}),
              ("　指南取 3 条，食物取 5 条", {"size": 9.5})],
     "spacing": 1.0},
])
hline(s, L, 3.26, 5.60, LINE2)

eyebrow(s, L, 3.38, "②  注入 · Augmentation")
box(s, L, 3.66, 5.60, 0.74, fill=LINE2)
rich(s, 1.10, 3.74, 5.30, 0.60, [
    {"runs": [("=== Food Nutrition Data (authoritative, do not fabricate) ===",
               {"size": 8.2, "color": BODY, "font": MONO})], "spacing": 1.25},
    {"runs": [("=== Dietary Guidelines (cite source when referencing) ===",
               {"size": 8.2, "color": BODY, "font": MONO})], "spacing": 1.25},
])
text(s, L, 4.54, 5.60, 0.30,
     "拼进 prompt，明确要求“不得编造、引用来源”。", 9.5, False, BODY, spacing=1.3)

hline(s, L, 4.96, 5.60, LINE2)
eyebrow(s, L, 5.08, "为什么不用向量库", MUTED)
text(s, L, 5.38, 5.50, 0.90,
     "语料只有 50 条食物 + 12 条指南，词表很小：TF-IDF 足够用，省掉 embedding 的成本和外部服务依赖，离线也能跑。",
     9.2, False, MUTED, spacing=1.4)

# right column cards
def kb_card(x, y, w, h, col, fname, sub, rec, mono):
    card(s, x, y, w, h, fill=CARD2, line=LINE)
    box(s, x, y, w, 0.04, fill=col)
    text(s, x + 0.25, y + 0.18, w - 0.50, 0.26, fname, 9.6, True, INK, font=MONO,
         spacing=1.0)
    text(s, x + 0.25, y + 0.48, w - 0.50, 0.26, sub, 9.3, False, BODY, spacing=1.1)
    hline(s, x + 0.25, y + 0.82, w - 0.50, LINE2)
    text(s, x + 0.25, y + 0.94, 0.80, 0.22, "每条记录", 8.4, True, MUTED, spacing=1.0)
    text(s, x + 1.10, y + 0.94, w - 1.35, 0.22, rec, 8.8, False, INK, spacing=1.0)
    text(s, x + 0.25, y + 1.24, w - 0.50, 0.24, mono, 8.5, False, MUTED, font=MONO,
         spacing=1.0)


kb_card(6.95, 1.96, 5.43, 1.86, GREEN, "rag/food_nutrition.json",
        "50 种常见食物（主食 / 肉蛋 / 蔬菜 / 乳制品…）",
        "名称 · 别名 · 每 100g 营养",
        "{ 热量 / 蛋白 / 碳水 / 脂肪 / 纤维 }")
kb_card(6.95, 3.94, 5.43, 1.86, PURPLE, "rag/dietary_guidelines.json",
        "12 条《中国居民膳食指南（2022）》",
        "分类 · 标题 · 权威建议原文",
        "引用时要求标注来源")
text(s, 6.95, 5.94, 5.43, 0.28,
     "启动时一次性加载进内存；无向量数据库、无额外服务，可离线运行。", 8.8, False,
     MUTED, spacing=1.15)

band(s, L, 6.42, CW, 0.44)
text(s, 1.20, 6.52, 11.00, 0.26,
     "权威数据写死在库里，AI 就没有编造的空间。", 9.8, True, DGREEN, spacing=1.0)

# ================================================================ slide 7 落地
s = new_slide(WHITE)
header(s, "RAG 落地：本地先算，算不出再问 AI", 7,
       lead="三个使用场景，共同目标：能本地搞定的，绝不调 AI。")

scenes = [
    (GREEN, "①", "食物识别（本地优先）",
     "“200g 牛肉” → 正则抓「数量 + 单位」→ 换算成克 → 查食物库命中 → 按 100g 比例算营养 → source = local-db",
     "只有本地 miss，才 fallback 到 AI 识别"),
    (BLUE, "②", "营养缺口解读",
     "用户近 7 天达标数据 → 注入膳食指南 → AI 引用权威建议",
     "输出 2–4 条可执行调整"),
    (PURPLE, "③", "AI 教练反馈",
     "画像 + 今日 / 近期数据 → 注入权威食物数据 → 生成 3–5 条行动建议",
     "优先参考权威数据，不自行估算数值"),
]
for i, (col, num, title, l1, l2) in enumerate(scenes):
    y = 1.96 + i * 1.04
    band(s, L, y, CW, 0.95, fill=RGBColor(0xF7, 0xF9, 0xF7))
    box(s, L, y, 0.04, 0.95, fill=col)
    text(s, 1.20, y + 0.12, 0.30, 0.26, num, 11, True, col, spacing=1.0)
    text(s, 1.52, y + 0.10, 3.10, 0.28, title, 11.5, True, INK, spacing=1.0)
    text(s, 1.52, y + 0.40, 9.70, 0.26, l1, 9.2, False, BODY, spacing=1.1)
    text(s, 1.52, y + 0.66, 9.70, 0.24, l2, 9.2, False, MUTED, spacing=1.0)

band(s, L, 5.15, CW, 1.05)
text(s, 1.20, 5.26, 6.00, 0.24, "单位换算 · tryLocalRecognize()", 9.0, True, DGREEN,
     spacing=1.0)
text(s, 1.20, 5.52, 10.90, 0.24,
     "kg / 公斤 = 1000g　　斤 = 500g　　两 = 50g　　个 / 只 = 100g", 9.2, False,
     BODY, font=MONO, spacing=1.0)
text(s, 1.20, 5.78, 10.90, 0.24,
     "份 = 200g　　碗 = 250g　　盘 = 300g　　杯 = 250g　　勺 = 10g", 9.2, False,
     BODY, font=MONO, spacing=1.0)

values = [("防幻觉", "权威数据写死在库，AI 不编数值"),
          ("省成本", "常见食物本地秒算，零 API 调用"),
          ("有依据", "建议可回溯到膳食指南来源")]
for i, (k, v) in enumerate(values):
    x = COLX[i]
    rich(s, x, 6.36, CW3, 0.28, [{"runs": [
        (k, {"bold": True, "size": 9.8, "color": DGREEN}),
        ("　" + v, {"size": 9.2, "color": BODY})], "spacing": 1.0}])

# ================================================================ slide 8 七分区
s = new_slide(WHITE)
header(s, "便利贴看板：七大类信息分区", 8,
       lead="一个页面看全你的「营养画像」，每一条都是可交互的标签。",
       lead_right="七大类  ·  一个看板")

W4 = (CW - 3 * 0.22) / 4
W3 = (CW - 2 * 0.22) / 3

tiles = [
    (C_BASE, "基础信息", "只读摘要", "性别 · 年龄 · 身高体重\n目标 · 地区 · 活动量",
     "纯展示，点「编辑」跳画像页"),
    (C_STATUS, "身体状态", "可交互", "高血压 · 乳糖不耐受\n术后恢复 · 孕期…",
     "AI 据此调整建议口径"),
    (C_LIKE, "喜欢吃", "可交互", "番茄 · 三文鱼 · 燕麦\n咖啡 · 麻辣…", "推荐时优先出现"),
    (C_DISLIKE, "不喜欢 / 过敏", "可交互", "香菜 · 花生 · 虾\n乳糖…", "过敏 / 忌口，AI 自动避开"),
    (C_FRIDGE, "冰箱里有", "可交互", "鸡胸肉 · 西兰花 · 鸡蛋\n牛奶 · 糙米…",
     "现有食材 → 触发「有料推荐」"),
    (C_PERSONA, "AI 角色设定", "可交互", "温柔女友风 · 古风\n毒舌教练 · 专业营养师…",
     "注入 System Prompt，改语气与称呼"),
    (C_GAP, "AI 营养提醒", "自动计算", "近 7 天营养缺口 + AI 洞察\n（热量 / 蛋白 / 纤维…）",
     "系统自动算，纯展示、不可点击"),
]


def tile(x, y, w, h, col, name, kind, body_text, hint, auto=False):
    card(s, x, y, w, h, fill=RGBColor(0xFB, 0xF7, 0xFE) if auto else WHITE,
         line=LINE)
    box(s, x, y, w, 0.04, fill=col)
    box(s, x + 0.22, y + 0.26, 0.11, 0.11, fill=col)
    text(s, x + 0.42, y + 0.19, w - 0.60, 0.28, name, 11, True, INK, spacing=1.0)
    text(s, x + 0.22, y + 0.58, w - 0.40, 0.22, kind, 8.2, True,
         MAGENTA if auto else MUTED, spacing=1.0)
    text(s, x + 0.22, y + 0.90, w - 0.44, 0.60, body_text, 9.0, False, BODY,
         spacing=1.45)
    text(s, x + 0.22, y + h - 0.46, w - 0.44, 0.36, hint, 8.4, False, MUTED,
         spacing=1.2)


for i in range(4):
    tile(L + i * (W4 + 0.22), 1.96, W4, 2.10, *tiles[i])
for i in range(3):
    tile(L + i * (W3 + 0.22), 4.20, W3, 2.05, *tiles[4 + i], auto=(i == 2))

band(s, L, 6.38, CW, 0.44)
rich(s, 1.15, 6.50, 11.00, 0.24, [{"runs": [
    ("数据来源分离", {"bold": True, "size": 8.8, "color": DGREEN}),
    ("　手动维护 → 数据库　　系统自动算 → 后端 gap 字段实时计算　　只读摘要 → 画像硬数据",
     {"size": 8.8, "color": BODY})], "spacing": 1.0}])

# ================================================================ slide 9 便利贴
s = new_slide(WHITE)
header(s, "便利贴：在对话中「引用提及」标签", 9,
       lead="看板标签 = 信息本体；便利贴 = 对话引用。不是「送达 / 未送达」的关系。")

card(s, L, 1.96, 5.55, 1.02, fill=CARD, line=None, accent=GREEN)
text(s, 1.22, 2.10, 5.10, 0.24, "看板标签 · 信息本体", 10, True, DGREEN, spacing=1.0)
text(s, 1.22, 2.38, 5.00, 0.50, "AI 每轮对话都自动读全，不勾选、不拖动也在。", 9.3,
     False, BODY, spacing=1.3)

card(s, 6.83, 1.96, 5.55, 1.02, fill=RGBColor(0xF9, 0xF6, 0xFC), line=None,
     accent=PURPLE)
text(s, 7.10, 2.10, 5.10, 0.24, "便利贴 · 对话引用", 10, True, PURPLE, spacing=1.0)
text(s, 7.10, 2.38, 5.00, 0.50, "这轮想重点聊哪几条，点一下就说清楚。", 9.3, False,
     BODY, spacing=1.3)

band(s, L, 3.30, CW, 1.62, fill=CARD2)
boxes = [(1.55, "看板标签", GREEN, "AI 本来就读得到（不拖动也在）", "拖拽"),
         (5.50, "便利贴", PURPLE, "本轮重点提名", "点击"),
         (9.45, "对话", BLUE, "AI 围绕它回应", None)]
for x, label, col, cap, verb in boxes:
    box(s, x, 3.52, 2.30, 0.56, fill=WHITE, line=LINE, lw=1.0,
        shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=0.14)
    text(s, x, 3.66, 2.30, 0.26, label, 10, True, INK, align=PP_ALIGN.CENTER,
         spacing=1.0)
    text(s, x - 0.35, 4.22, 3.00, 0.44, cap, 8.8, False, MUTED,
         align=PP_ALIGN.CENTER, spacing=1.25)
    if verb:
        rich(s, x + 2.32, 3.62, 1.62, 0.26, [{
            "runs": [("→　", {"size": 10, "color": FAINT}),
                     (verb, {"size": 8.8, "color": MUTED})],
            "align": PP_ALIGN.CENTER, "spacing": 1.0}])

text(s, L, 5.28, 8.00, 0.24, "便利贴的价值：不是「喂信息」，是「点名」。", 9.5, True,
     INK, spacing=1.0)
items = [
    ("想聊冰箱里的鸡胸肉 → 拖到便利贴 → 点击，AI 围绕它回应", L, 5.60),
    ("「全部挂到对话」→ 这一批标签一次性塞进当前消息", 6.83, 5.60),
    ("「＋ 写一个便利贴…」→ 手写自定义，临时补一句", L, 6.02),
    ("本地持久化 localStorage nutriai_rail_picks_v2", 6.83, 6.02),
]
for t, x, y in items:
    text(s, x, y, 0.14, 0.24, "·", 9.5, True, GREEN, spacing=1.0)
    text(s, x + 0.20, y, 5.20, 0.30, t, 9.3, False, BODY, spacing=1.25)

text(s, L, 6.44, 11.43, 0.30,
     "标签本身不变，变的只是这一轮的注意力。", 10, True, DGREEN, spacing=1.0)

# ================================================================ slide 10 营养提醒
s = new_slide(WHITE)
header(s, "AI 营养提醒：系统自动计算的标签", 10,
       lead="为什么它特殊？因为不用用户动手，AI 自己算。")

# comparison table
text(s, 2.10, 1.98, 2.50, 0.24, "其他六个分区", 8.5, True, MUTED, spacing=1.0)
text(s, 4.75, 1.98, 2.90, 0.24, "AI 营养提醒", 8.5, True, MAGENTA, spacing=1.0)
hline(s, L, 2.24, 6.80, LINE)

trows = [("来源", "用户手动添加", "系统自动计算"),
         ("数据", "喜好 / 忌口 / 冰箱 / 角色", "营养缺口（近 7 天饮食分析）"),
         ("交互", "点击标签 → 发给 AI", "纯展示，不可点击"),
         ("样式", "实线边框 + 纯色", "虚线边框 + 紫粉渐变 + 顶部渐变条")]
for i, (k, a, b) in enumerate(trows):
    y = 2.36 + i * 0.54
    text(s, 1.10, y, 0.90, 0.24, k, 9.0, True, MUTED, spacing=1.0)
    text(s, 2.10, y, 2.50, 0.26, a, 9.0, False, BODY, spacing=1.0)
    text(s, 4.75, y, 2.95, 0.26, b, 9.0, False, INK, spacing=1.0)
    hline(s, L, y + 0.42, 6.80, LINE2)

# gap grading
text(s, L, 4.72, 6.80, 0.22, "缺口分级", 9.0, True, MUTED, spacing=1.0)
grades = [(RED, "严重不足", "< 40%", "优先补"),
          (ORANGE, "偏低", "< 80%", "下一餐调整"),
          (GREEN, "正常", "< 110%", "保持")]
for i, (col, name, rng, act) in enumerate(grades):
    y = 5.00 + i * 0.34
    box(s, 1.10, y + 0.07, 0.09, 0.09, fill=col)
    text(s, 1.32, y, 1.10, 0.22, name, 9.0, True, INK, spacing=1.0)
    text(s, 2.55, y, 0.90, 0.22, rng, 8.8, False, BODY, font=MONO, spacing=1.0)
    text(s, 3.60, y, 1.60, 0.22, act, 8.8, False, MUTED, spacing=1.0)
text(s, 5.30, 5.00, 2.50, 0.90,
     "颜色即结论：红 / 橙 / 绿三档，不需要用户自己换算。", 8.8, False, MUTED,
     spacing=1.35)

# mock card (right)
mx, my, mw, mh = 8.15, 1.92, 4.23, 3.66
box(s, mx, my, mw, mh, fill=WHITE, line=RGBColor(0xD8, 0xB4, 0xFE), lw=1.1,
    dash=MSO_LINE_DASH_STYLE.DASH, shape=MSO_SHAPE.ROUNDED_RECTANGLE,
    radius=0.045)
gradient_bar(s, mx + 0.07, my + 0.01, mw - 0.14, 0.06, C_PERSONA,
             RGBColor(0xF0, 0xAB, 0xFC))
text(s, mx + 0.30, my + 0.22, 2.50, 0.26, "AI 营养提醒", 12, True, PURPLE,
     spacing=1.0)
text(s, mx + 2.50, my + 0.26, 1.43, 0.22, "系统计算", 8.4, False, MUTED,
     align=PP_ALIGN.RIGHT, spacing=1.0)
text(s, mx + 0.30, my + 0.54, 3.60, 0.22, "近 7 天 · 12 餐记录", 8.6, False, MUTED,
     spacing=1.0)

bars = [(ORANGE, "热量", 0.60, "60%", "偏低"),
        (GREEN, "蛋白质", 0.80, "80%", "正常"),
        (RED, "膳食纤维", 0.20, "20%", "严重不足")]
for i, (col, name, ratio, pct, grade) in enumerate(bars):
    y = my + 0.86 + i * 0.32
    text(s, mx + 0.30, y, 1.00, 0.22, name, 8.8, False, BODY, spacing=1.0)
    box(s, mx + 1.30, y + 0.07, 1.62, 0.09, fill=RGBColor(0xED, 0xE9, 0xF5))
    box(s, mx + 1.30, y + 0.07, 1.62 * ratio, 0.09, fill=col)
    text(s, mx + 3.00, y - 0.01, 0.52, 0.22, pct, 8.4, True, col, font=MONO,
         align=PP_ALIGN.RIGHT, spacing=1.0)
    text(s, mx + 3.55, y - 0.01, 0.55, 0.22, grade, 8.0, False, MUTED, spacing=1.0)

box(s, mx + 0.30, my + 1.92, mw - 0.60, 0.92, fill=RGBColor(0xFB, 0xF5, 0xFF),
    line=RGBColor(0xE9, 0xD5, 0xFF), lw=0.9)
text(s, mx + 0.46, my + 2.00, 2.00, 0.22, "AI 洞察", 8.4, True, MAGENTA, spacing=1.0)
text(s, mx + 0.46, my + 2.22, mw - 0.92, 0.56,
     "蔬菜摄入明显偏少，建议中午加一份深色蔬菜，晚餐用杂粮替白米饭。", 8.5, False,
     BODY, spacing=1.25)
text(s, mx + 0.30, my + 2.98, mw - 0.60, 0.22,
     "虚线 + 渐变 = 这不是你写的标签", 8.2, False, MUTED, spacing=1.0)
text(s, mx + 0.30, my + 3.22, mw - 0.60, 0.22,
     "它会和用户标签一样被拼进上下文", 8.2, False, MUTED, spacing=1.0)

text(s, L, 6.40, 11.43, 0.30,
     "系统标签也会进入 AI 上下文，只是没有勾选、没有点击。", 10, True, DGREEN,
     spacing=1.0)

# ================================================================ slide 11 稳健性
s = new_slide(WHITE)
header(s, "稳健性修复", 11,
       lead="这一周不堆新功能，把已有的打磨到“不会翻车”。")
text(s, 4.80, 1.94, 3.30, 0.22, "问题", 8.5, True, MUTED, spacing=1.0)
text(s, 8.40, 1.94, 3.95, 0.22, "修复", 8.5, True, MUTED, spacing=1.0)

fixes = [
    (GREEN, "①", "序数指代餐别不再写死",
     "“就选第一个吧”，餐别硬编码“午餐”，跨时段会记错。",
     "mealTypeForHour(hour) 按时段推断：5–10 早餐 / 10–14 午餐 / 14–17 加餐 / 17–22 晚餐。序数指代直接映射前端 suggestedRecipes，绕过 AI 食物识别。"),
    (ORANGE, "②", "落库空值兜底",
     "新增日记时 amountGram 传 null，会原样写进数据库，前端按 0 算却显示为空。",
     "新增分支 null → 0.0；更新分支保留显式清空语义，不把「清空」误判成「没填」。"),
    (BLUE, "③", "核心逻辑回归测试",
     "新增逻辑没有回归保护，改动全靠肉眼验证，改坏了也不会立刻发现。",
     "AssistantIntentServiceTest 31 → 34 条，新增序号解析 / 序号守卫 / 时段餐别映射；mvn test 34 / 34 全通过。"),
    (PURPLE, "④", "Fat JAR 打包修复",
     "spring-boot-maven-plugin 缺 executions，package 只产出瘦 jar，换台机器就跑不起来。",
     "绑定 repackage goal；mvn clean package 生成 57MB Fat JAR，java -jar 可直接启动。"),
]
for i, (col, num, title, prob, fix) in enumerate(fixes):
    y = 2.22 + i * 0.94
    if i:
        hline(s, L, y - 0.10, CW, LINE2)
    text(s, L, y + 0.02, 0.40, 0.26, num, 11, True, col, spacing=1.0)
    text(s, 1.50, y - 0.02, 3.10, 0.52, title, 11, True, INK, spacing=1.2)
    text(s, 4.80, y - 0.02, 3.35, 0.90, prob, 9.2, False, MUTED, spacing=1.35)
    text(s, 8.40, y - 0.02, 3.96, 0.95, fix, 9.2, False, BODY, spacing=1.35)

text(s, L, 6.18, 11.43, 0.30,
     "演示前先把翻车的可能性清零——这四项都在堵这个。", 10, True, DGREEN,
     spacing=1.0)

# ================================================================ slide 12 演示①
s = new_slide(WHITE)
header(s, "效果演示 ①  主界面与看板", 12,
       lead="左侧便利贴、中间对话、右侧七分区看板。",
       lead_right="截图位已留好 · 素材见 docs/screenshots/")


def shot(x, y, w, h, fname, res):
    box(s, x, y, w, h, fill=CARD2, line=MUTED, lw=0.9,
        dash=MSO_LINE_DASH_STYLE.DASH)
    mid = y + h / 2
    text(s, x, mid - 0.30, w, 0.26, fname, 9.5, False, MUTED, font=MONO,
         align=PP_ALIGN.CENTER, spacing=1.0)
    text(s, x, mid - 0.02, w, 0.24, res, 8.0, False, FAINT, align=PP_ALIGN.CENTER,
         spacing=1.0)
    text(s, x + w - 0.66, y + h - 0.30, 0.56, 0.22, "16 : 9", 7.5, False, FAINT,
         align=PP_ALIGN.RIGHT, spacing=1.0)


shot(L, 1.96, 5.55, 3.12, "02-assistant.png", "2560 × 1440   ·  替换此图")
shot(6.83, 1.96, 5.55, 3.12, "03-board.png", "2560 × 1440   ·  替换此图")

text(s, L, 5.18, 5.55, 0.28, "AI 对话助手主界面", 10.5, True, INK, spacing=1.0)
text(s, L, 5.44, 5.55, 0.26, "左侧便利贴 + 中间对话区 + 底部输入框", 9.0, False,
     MUTED, spacing=1.0)
text(s, 6.83, 5.18, 5.55, 0.28, "便利贴看板全景", 10.5, True, INK, spacing=1.0)
text(s, 6.83, 5.44, 5.55, 0.26, "七大类信息分区完整展示", 9.0, False, MUTED,
     spacing=1.0)

band(s, L, 5.82, CW, 0.98)
notes = [("看板分区", "各分区标签 + 「＋」添加按钮"),
         ("便利贴区", "可拖拽、点击即发送"),
         ("对话气泡", "写操作前弹确认卡")]
for i, (k, v) in enumerate(notes):
    x = COLX[i]
    box(s, x, 6.04, 0.09, 0.09, fill=GREEN)
    text(s, x + 0.20, 5.96, CW3 - 0.20, 0.24, k, 9.2, True, INK, spacing=1.0)
    text(s, x + 0.20, 6.22, CW3 - 0.20, 0.40, v, 8.8, False, MUTED, spacing=1.25)

# ================================================================ slide 13 演示②
s = new_slide(WHITE)
header(s, "效果演示 ②  画像 → 推荐 · 深色 / 移动端", 13,
       lead="从填表到推荐，以及在深色与窄屏下的可用性。",
       lead_right="截图位已留好 · 素材见 docs/screenshots/")

shots = [
    (L, 1.92, "06-profile.png", "画像表单", "含「计算推荐目标」"),
    (4.45, 1.92, "06b-result.png", "结构化推荐结果", "食谱卡片 + 营养信息"),
    (L, 4.24, "07-dark-assistant.png", "深色模式", "看板与对话区自适应"),
    (4.45, 4.24, "09-mobile-assistant.png", "移动端", "窄屏下单栏重排"),
]
for x, y, fname, title, desc in shots:
    shot(x, y, 3.30, 1.86, fname, "替换此图")
    text(s, x, y + 1.92, 3.30, 0.22, title, 9.8, True, INK, spacing=1.0)
    text(s, x, y + 2.12, 3.30, 0.20, desc, 8.4, False, MUTED, spacing=1.0)

text(s, 8.15, 1.92, 4.23, 0.24, "标注要点", 9.5, True, INK, spacing=1.0)
rich(s, 8.15, 2.22, 4.23, 1.70, [
    {"runs": [("·  ", {"size": 9.2, "color": GREEN}),
              ("画像表单 → 一键算出推荐热量 / 三大营养素", {"size": 9.2})],
     "spacing": 1.35, "after": 8},
    {"runs": [("·  ", {"size": 9.2, "color": GREEN}),
              ("推荐结果卡片（可收藏 / 记入今日饮食）", {"size": 9.2})],
     "spacing": 1.35, "after": 8},
    {"runs": [("·  ", {"size": 9.2, "color": GREEN}),
              ("深色模式与移动端：看板、对话区自适应正常", {"size": 9.2})],
     "spacing": 1.35},
])

hline(s, 8.15, 4.28, 4.23, LINE2)
text(s, 8.15, 4.40, 4.23, 0.24, "插入方式", 9.0, True, MUTED, spacing=1.0)
text(s, 8.15, 4.66, 4.23, 1.40,
     "选中虚线框 → 右键「更改图片」，按原 16:9 比例替换；移动端为竖屏 9:16，可保留左右留白或单独裁一张。",
     8.6, False, MUTED, spacing=1.5)

# ================================================================ slide 14 回顾
s = new_slide(WHITE)
header(s, "核心技术点回顾", 14)

recap = [
    (GREEN, "01", "信息标签化", "七类信息 → 统一标签 → 看板集中管理；AI 每轮自动读全，便利贴只做「对话引用」。"),
    (ORANGE, "02", "规则优先、AI 兜底", "18 种规则意图即时完成、零 API 成本；长尾交给 AI，且必须带真实上下文。"),
    (PURPLE, "03", "本地 RAG（防幻觉）", "50 种食物营养库 + 12 条膳食指南，检索后注入 prompt；本地能算就不调 AI。"),
    (BLUE, "04", "体重提取三层反排除", "宽松正则 → 反排除假阳性（食材量 / 价格）→ 关键词兜底。"),
    (MAGENTA, "05", "稳健性收口", "意图 / 落库 / 打包 / 测试全链路加固，34 / 34 测试通过。"),
    (DGREEN, "06", "差异化视觉层级", "用户标签 = 实线纯色；系统标签 = 虚线 + 紫粉渐变。"),
]
for i, (col, num, title, desc) in enumerate(recap):
    y = 1.86 + i * 0.72
    if i:
        hline(s, L, y - 0.06, CW, LINE2)
    text(s, 0.97, y + 0.05, 0.50, 0.26, num, 10, True, col, font=MONO, spacing=1.0)
    text(s, 1.55, y + 0.02, 3.00, 0.28, title, 12, True, INK, spacing=1.0)
    text(s, 4.70, y - 0.02, 7.62, 0.54, desc, 9.8, False, BODY, spacing=1.3)

hline(s, L, 6.20, CW, LINE)
text(s, L, 6.34, 11.43, 0.34,
     "把信息变成标签，让 AI 真正“读懂”你。", 13, True, DGREEN, spacing=1.0)

# ================================================================ slide 15 致谢
s = new_slide(DEEP)
box(s, L, 2.00, 0.05, 2.55, fill=RGBColor(0x5E, 0x9A, 0x63))
text(s, 1.35, 2.05, 9.00, 0.90, "感谢聆听", 42, True, WHITE, spacing=1.0)
text(s, 1.35, 3.00, 9.00, 0.40, "欢迎提问", 17, False,
     RGBColor(0xC4, 0xD9, 0xC5), spacing=1.0)
box(s, 1.35, 3.66, 2.20, 0.01, fill=RGBColor(0x3A, 0x5A, 0x3E))
text(s, 1.35, 3.86, 10.00, 0.30,
     "NutriAI  ·  AI 对话助手  ·  便利贴看板  ·  信息标签化  ·  本地 RAG", 11, False,
     RGBColor(0xA8, 0xC3, 0xAA), spacing=1.0)
text(s, 1.35, 4.16, 10.00, 0.30, "第八周汇报  ·  2026-11-11", 11, False,
     RGBColor(0x7C, 0x9C, 0x7F), spacing=1.0)
box(s, L, 7.02, CW, 0.01, fill=RGBColor(0x3A, 0x5A, 0x3E))
text(s, L, 7.08, 7.00, 0.26, "NutriAI  ·  Week 8  ·  2026-11-11", 8.5, False,
     RGBColor(0x6F, 0x8B, 0x73), spacing=1.0)
text(s, 10.98, 7.08, 1.40, 0.26, f"15 / {TOTAL}", 8.5, False,
     RGBColor(0x6F, 0x8B, 0x73), align=PP_ALIGN.RIGHT, spacing=1.0)

# ---------------------------------------------------------------- save
out = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "NutriAI_Week8_Report.pptx")
prs.save(out)
print("saved:", out, "slides:", len(prs.slides.__iter__.__self__._sldIdLst))
