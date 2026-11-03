#!/usr/bin/env python3
"""Optional report export: pip install reportlab python-pptx python-docx.

Needs a Unicode TrueType font, selected with AUCTION_DOC_FONT / AUCTION_DOC_FONT_BOLD.
The application and its build do not depend on these Python packages.
"""
import os
from pathlib import Path
import re
from xml.sax.saxutils import escape

from docx import Document
from docx.shared import Pt
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.util import Inches, Pt as SlidePt
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Image

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs'
FONT = os.environ.get('AUCTION_DOC_FONT', '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')
BOLD = os.environ.get('AUCTION_DOC_FONT_BOLD', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf')


def plain(text):
    text = re.sub(r'\[([^]]+)\]\([^)]+\)', r'\1', text)
    return text.replace('**', '').replace('`', '')


def paragraphs(markdown):
    for block in re.split(r'\n\s*\n', markdown):
        if block.strip():
            yield plain(block.strip())


def report():
    pdfmetrics.registerFont(TTFont('Gavel', FONT))
    pdfmetrics.registerFont(TTFont('GavelBold', BOLD))
    styles = {
        'body': ParagraphStyle('Body', fontName='Gavel', fontSize=10, leading=16, spaceAfter=10, alignment=TA_LEFT),
        'title': ParagraphStyle('Title', fontName='GavelBold', fontSize=22, leading=30, textColor=colors.HexColor('#142d3c'), spaceAfter=22),
        'heading': ParagraphStyle('Heading', fontName='GavelBold', fontSize=14, leading=21, textColor=colors.HexColor('#8a6830'), spaceBefore=14, spaceAfter=10),
    }
    story = []
    word = Document()
    word.styles['Normal'].font.name = 'Calibri'
    word.styles['Normal'].font.size = Pt(11)
    for block in paragraphs((DOCS / 'report.md').read_text()):
        if block.startswith('# '):
            title = block[2:]
            story.append(Paragraph(escape(title), styles['title']))
            word.add_heading(title, 0)
        elif block.startswith('## '):
            heading = block[3:]
            story.append(Paragraph(escape(heading), styles['heading']))
            word.add_heading(heading, 1)
        else:
            text = ' '.join(block.splitlines())
            story.append(Paragraph(escape(text), styles['body']))
            word.add_paragraph(text)
    screenshot = DOCS / 'images/auction-live.png'
    if screenshot.exists():
        story.extend([Spacer(1, 12), Paragraph('Giao diện từ kiểm thử kết nối server thật', styles['heading']), Image(str(screenshot), width=17 * cm, height=17 * 820 / 1280 * cm)])
        word.add_picture(str(screenshot), width=__import__('docx').shared.Inches(6))
    def footer(canvas, document):
        canvas.setFont('Gavel', 8)
        canvas.setFillColor(colors.HexColor('#667886'))
        canvas.drawString(2 * cm, 1.1 * cm, 'GAVEL • Bài tập lớn Lập trình nâng cao')
        canvas.drawRightString(19 * cm, 1.1 * cm, str(document.page))
    SimpleDocTemplate(str(DOCS / 'report.pdf'), pagesize=(21 * cm, 29.7 * cm), leftMargin=2 * cm, rightMargin=2 * cm, topMargin=1.7 * cm, bottomMargin=1.7 * cm).build(story, onFirstPage=footer, onLaterPages=footer)
    word.save(DOCS / 'report.docx')


def slides():
    deck = Presentation()
    deck.slide_width, deck.slide_height = Inches(13.333), Inches(7.5)
    navy, gold, muted = RGBColor.from_string('142D3C'), RGBColor.from_string('CDA467'), RGBColor.from_string('4B6170')
    sections = re.split(r'^## ', (DOCS / 'slides.md').read_text(), flags=re.M)[1:]
    for index, section in enumerate(sections, 1):
        heading, _, body = section.partition('\n')
        slide = deck.slides.add_slide(deck.slide_layouts[6])
        bar = slide.shapes.add_shape(1, 0, 0, deck.slide_width, Inches(0.16))
        bar.fill.solid(); bar.fill.fore_color.rgb = gold; bar.line.fill.background()
        title = slide.shapes.add_textbox(Inches(0.7), Inches(0.55), Inches(12), Inches(0.8)).text_frame
        title.text = heading
        title.paragraphs[0].font.size = SlidePt(31)
        title.paragraphs[0].font.bold = True
        title.paragraphs[0].font.color.rgb = navy
        text = slide.shapes.add_textbox(Inches(0.8), Inches(1.7), Inches(11.8), Inches(4.7)).text_frame
        text.word_wrap = True
        for i, block in enumerate(paragraphs(body)):
            paragraph = text.paragraphs[0] if i == 0 else text.add_paragraph()
            paragraph.text = ' '.join(block.splitlines())
            paragraph.font.size = SlidePt(23)
            paragraph.font.color.rgb = muted
            paragraph.space_after = SlidePt(25)
        footer = slide.shapes.add_textbox(Inches(0.8), Inches(6.9), Inches(11.8), Inches(0.3)).text_frame
        footer.text = f'GAVEL / LTNC 2026                                                    {index:02d}'
        footer.paragraphs[0].font.size = SlidePt(11)
        footer.paragraphs[0].font.color.rgb = muted
    screenshot = DOCS / 'images/auction-live.png'
    if screenshot.exists():
        slide = deck.slides.add_slide(deck.slide_layouts[6])
        slide.shapes.add_picture(str(screenshot), Inches(1.25), Inches(0.25), height=Inches(7))
    deck.save(DOCS / 'presentation.pptx')


if __name__ == '__main__':
    report()
    slides()
    print('Exported docs/report.pdf, report.docx, presentation.pptx')
