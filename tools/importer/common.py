"""导入器公共设施：路径、日志、JSONL 读写、卷型归一。

设计约定（踩坑后固化的规则）：
1. CJK 文本分析全部走 Python 正则，不用 grep（对 UTF-8 中文不可靠）。
2. CJK 路径不通过命令行内联传参（会 mojibake），一律用原始字符串基路径 + os.listdir 拼接。
3. Windows 控制台是 GBK，所有人类可读输出写 UTF-8 文件，不直接 print 中文。
"""
from __future__ import annotations

import io
import json
import os
import re
import sys
import warnings

warnings.filterwarnings('ignore')

HERE = os.path.dirname(os.path.abspath(__file__))
# 题库素材根目录，可通过 HONGHU_IMPORT_SOURCE_ROOT 指定。
SOURCE_ROOT = os.path.abspath(os.environ.get(
    'HONGHU_IMPORT_SOURCE_ROOT', os.path.join(HERE, 'source')))
BUILD_DIR = os.path.join(HERE, 'build')

os.makedirs(BUILD_DIR, exist_ok=True)


# ---------- 源目录 ----------

def src(*parts: str) -> str:
    return os.path.join(SOURCE_ROOT, *parts)


DIR_GK_XINGCE_STEM = src('2011-2025国考行测真题')
DIR_GK_XINGCE_ANSWER = src('国考历年真题试卷（2000-2024带解析版）',
                           '2000-2024国考行测PDF（带解析版）', '行测-答案及解析')
DIR_GK_XINGCE_STEM_ALT = src('国考历年真题试卷（2000-2024带解析版）',
                             '2000-2024国考行测PDF（带解析版）', '行测-真题')
DIR_GK_SHENLUN = src('国考历年真题试卷（2000-2024带解析版）', '2000-2024国考申论PDF（带答案版）')
DIR_GK_SHENLUN_ALT = src('国考历年真题试卷（2000-2024带解析版）', '国考申论2000年-2023年（带答案版）')
FILE_CHANGSHI_660 = src('《2024常识考点660篇》常识汇总（21年8月-24年10月）.pdf')
DIR_XINGCE_5000 = src('行测5000题【2024年6月版次】')


# ---------- 日志 ----------

class Report:
    """把人类可读的进度与统计写进 UTF-8 文件，避免 Windows 控制台编码问题。"""

    def __init__(self, name: str):
        self.path = os.path.join(BUILD_DIR, name)
        self._fh = io.open(self.path, 'w', encoding='utf-8')

    def line(self, *parts) -> None:
        text = ' '.join(str(p) for p in parts)
        self._fh.write(text + '\n')
        self._fh.flush()

    def section(self, title: str) -> None:
        self.line('')
        self.line('=' * 78)
        self.line(title)
        self.line('=' * 78)

    def close(self) -> None:
        self._fh.close()


def progress(msg: str) -> None:
    """只输出 ASCII 的进度提示，中文一律进报告文件。"""
    sys.stdout.write(msg + '\n')
    sys.stdout.flush()


# ---------- JSONL ----------

def write_jsonl(path: str, rows) -> int:
    count = 0
    with io.open(path, 'w', encoding='utf-8') as fh:
        for row in rows:
            fh.write(json.dumps(row, ensure_ascii=False) + '\n')
            count += 1
    return count


def read_jsonl(path: str):
    if not os.path.isfile(path):
        return
    with io.open(path, encoding='utf-8') as fh:
        for line in fh:
            line = line.strip()
            if line:
                yield json.loads(line)


def build_path(name: str) -> str:
    return os.path.join(BUILD_DIR, name)


# ---------- 试卷标识归一 ----------

YEAR_RE = re.compile(r'(19|20)\d{2}')

# 卷型归一：同一套卷在题干侧与答案侧的叫法常常不同（省级/副省、市地/地市），
# 不归一就配不上对。
PAPER_TYPE_RULES = [
    ('副省', ('副省', '省级', '省部级')),
    ('地市', ('地市', '市地', '地市级', '市地级')),
    ('执法', ('执法',)),
    ('A卷', ('（A卷）', 'A卷')),
    ('B卷', ('（B卷）', 'B卷')),
    ('卷一', ('卷（一）', '（一）')),
    ('卷二', ('卷（二）', '（二）')),
]


def parse_year(filename: str):
    m = YEAR_RE.search(filename)
    return int(m.group(0)) if m else None


def parse_paper_type(filename: str) -> str:
    for canonical, needles in PAPER_TYPE_RULES:
        for needle in needles:
            if needle in filename:
                return canonical
    return '单卷'


def paper_key(filename: str):
    """(年份, 卷型) 归一化键，用于跨文件夹配对题干与答案。"""
    year = parse_year(filename)
    return (year, parse_paper_type(filename)) if year else None


def list_pdfs(directory: str):
    """列目录下的 PDF。必须守卫 isfile：源数据里存在名字像 PDF 的目录。"""
    if not os.path.isdir(directory):
        return []
    out = []
    for name in sorted(os.listdir(directory)):
        full = os.path.join(directory, name)
        if os.path.isfile(full) and name.lower().endswith('.pdf'):
            out.append(full)
    return out


def list_pdfs_recursive(directory: str):
    """递归列出 PDF，跳过目录伪装文件名。"""
    if not os.path.isdir(directory):
        return []
    out = []
    for root, _dirs, names in os.walk(directory):
        for name in sorted(names):
            full = os.path.join(root, name)
            if os.path.isfile(full) and name.lower().endswith('.pdf'):
                out.append(full)
    return sorted(out)
