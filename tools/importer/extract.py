"""阶段 1：PDF → 原始文本 JSONL 缓存。

单独成阶段是因为解析 828 页的大 PDF 很慢，后续调解析正则时不应该反复重解 PDF。
缓存按「文件路径 + 大小 + mtime」判定是否失效。
"""
from __future__ import annotations

import hashlib
import io
import json
import os
import warnings

warnings.filterwarnings('ignore')
import pdfplumber

from common import (BUILD_DIR, DIR_GK_SHENLUN, DIR_GK_SHENLUN_ALT, DIR_GK_XINGCE_ANSWER,
                    DIR_GK_XINGCE_STEM, DIR_GK_XINGCE_STEM_ALT, DIR_XINGCE_5000,
                    FILE_CHANGSHI_660, Report, build_path, list_pdfs, list_pdfs_recursive,
                    progress)

CACHE_DIR = os.path.join(BUILD_DIR, 'text_cache')
os.makedirs(CACHE_DIR, exist_ok=True)


def cache_key(path: str) -> str:
    st = os.stat(path)
    raw = f'{path}|{st.st_size}|{int(st.st_mtime)}'
    return hashlib.sha1(raw.encode('utf-8')).hexdigest()


def extract_text(path: str) -> dict:
    """返回 {pages: [...], chars: n}。逐页保留，方便定位页眉页脚。"""
    key = cache_key(path)
    cache_file = os.path.join(CACHE_DIR, key + '.json')
    if os.path.isfile(cache_file):
        with io.open(cache_file, encoding='utf-8') as fh:
            return json.load(fh)

    pages = []
    with pdfplumber.open(path) as pdf:
        for page in pdf.pages:
            pages.append(page.extract_text() or '')

    data = {
        'source_path': path,
        'source_name': os.path.basename(path),
        'pages': pages,
        'page_count': len(pages),
        'chars': sum(len(p) for p in pages),
    }
    with io.open(cache_file, 'w', encoding='utf-8') as fh:
        json.dump(data, fh, ensure_ascii=False)
    return data


def gather_targets():
    """待抽取的 PDF 清单，按用途分组。"""
    targets = []
    for path in list_pdfs(DIR_GK_XINGCE_STEM):
        targets.append(('gk_xingce_stem', path))
    for path in list_pdfs(DIR_GK_XINGCE_STEM_ALT):
        targets.append(('gk_xingce_stem_alt', path))
    for path in list_pdfs(DIR_GK_XINGCE_ANSWER):
        targets.append(('gk_xingce_answer', path))
    for path in list_pdfs(DIR_GK_SHENLUN):
        targets.append(('gk_shenlun', path))
    for path in list_pdfs(DIR_GK_SHENLUN_ALT):
        targets.append(('gk_shenlun_alt', path))
    if os.path.isfile(FILE_CHANGSHI_660):
        targets.append(('changshi_660', FILE_CHANGSHI_660))
    for path in list_pdfs_recursive(DIR_XINGCE_5000):
        # ponytail: skip the 194 MB scan-heavy volume that exhausts pdfplumber
        # memory; the other real volumes plus national exams cover the import target.
        if os.path.getsize(path) <= 100 * 1024 * 1024:
            targets.append(('xingce_5000', path))
    return targets


def main() -> None:
    report = Report('extract_report.txt')
    targets = gather_targets()
    report.section('阶段 1：PDF 文本抽取')
    report.line('待处理文件数：', len(targets))

    index = []
    scanned_zero = 0
    for i, (group, path) in enumerate(targets, 1):
        try:
            data = extract_text(path)
        except Exception as exc:  # 损坏的 PDF 不应中断整条流水线
            report.line('[ERROR]', os.path.basename(path), type(exc).__name__, str(exc)[:120])
            progress(f'  [{i}/{len(targets)}] ERROR')
            continue

        if data['chars'] == 0:
            scanned_zero += 1
            report.line('[SCAN-ONLY 0字符，跳过]', group, data['source_name'])
        index.append({
            'group': group,
            'source_path': path,
            'source_name': data['source_name'],
            'page_count': data['page_count'],
            'chars': data['chars'],
            'cache': cache_key(path) + '.json',
        })
        progress(f'  [{i}/{len(targets)}] {data["chars"]} chars')

    out = build_path('extract_index.jsonl')
    with io.open(out, 'w', encoding='utf-8') as fh:
        for row in index:
            fh.write(json.dumps(row, ensure_ascii=False) + '\n')

    report.section('抽取汇总')
    by_group = {}
    for row in index:
        g = by_group.setdefault(row['group'], {'files': 0, 'chars': 0, 'empty': 0})
        g['files'] += 1
        g['chars'] += row['chars']
        if row['chars'] == 0:
            g['empty'] += 1
    for group, stats in sorted(by_group.items()):
        report.line(f'{group:24s} 文件 {stats["files"]:3d}  字符 {stats["chars"]:9d}  '
                    f'扫描件(0字符) {stats["empty"]:3d}')
    report.line('')
    report.line('索引输出：', out)
    report.line('纯扫描件（无法解析，已标记跳过）：', scanned_zero)
    report.close()
    progress('extract done -> ' + out)


if __name__ == '__main__':
    main()
