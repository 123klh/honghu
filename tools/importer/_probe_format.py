"""抽查 PDF 实际文本格式，确认解析正则。CJK 路径必须用原始字符串基路径 + os.listdir 拼接。"""
import os
import re
import warnings

warnings.filterwarnings('ignore')
import pdfplumber

BASE = r'D:\复刻lk'


def read_pdf(path, max_pages=None):
    out = []
    with pdfplumber.open(path) as pdf:
        pages = pdf.pages if max_pages is None else pdf.pages[:max_pages]
        for pg in pages:
            out.append(pg.extract_text() or '')
    return '\n'.join(out)


OUT = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '_probe_format.txt'),
           'w', encoding='utf-8')


def emit(*parts):
    OUT.write(' '.join(str(p) for p in parts) + '\n')


def probe(label, path, pages=4, show=2200):
    emit('=' * 78)
    emit(label)
    emit('path exists:', os.path.isfile(path))
    if not os.path.isfile(path):
        return
    txt = read_pdf(path, pages)
    emit(f'chars in first {pages} pages: {len(txt)}')
    emit('-' * 78)
    emit(txt[:show])


# 1. 国考行测真题（题干侧）
d1 = os.path.join(BASE, '2011-2025国考行测真题')
files1 = sorted(os.listdir(d1))
t2022 = [f for f in files1 if f.startswith('2022') and '副省' in f]
emit('2022 副省 candidates:', t2022)
if t2022:
    probe('题干侧: ' + t2022[0], os.path.join(d1, t2022[0]))

# 2. 答案及解析侧
d2 = os.path.join(BASE, '国考历年真题试卷（2000-2024带解析版）',
                  '2000-2024年国考行测PDF（带解析版）', '行测-答案及解析')
if os.path.isdir(d2):
    files2 = sorted(os.listdir(d2))
    a2022 = [f for f in files2 if f.startswith('2022')]
    print('\n2022 answer candidates:', a2022)
    if a2022:
        probe('答案侧: ' + a2022[0], os.path.join(d2, a2022[0]))

OUT.close()
