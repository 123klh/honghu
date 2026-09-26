"""Stage 3: normalize parsed candidates into load-ready rows.

Input  : build/parsed.jsonl   (candidates from parse.py)
Output : build/normalized.jsonl, build/normalize_report.txt,
         build/normalize_rejects.jsonl, build/sample_20.txt

Normalization is deliberately conservative.  Every rule here either removes
page furniture that no question can legitimately contain, or repairs a known
PDF artefact.  Rules that could damage real content (collapsing repeated Han
characters, stripping digits) are narrowly scoped and unit tested in
test_normalize.py.
"""
from __future__ import annotations

import collections
import io
import json
import random
import re
import unicodedata

from common import build_path, progress, read_jsonl, write_jsonl

# Kangxi radicals / CJK compatibility forms some PDF producers emit instead of
# the normal Han character.  NFKC already maps most of these; the map covers
# the ones it leaves alone.
RADICAL_MAP = {
    '⼀': '一', '⼆': '二', '⼈': '人', '⼉': '儿', '⼊': '入', '⼋': '八',
    '⼗': '十', '⼚': '厂', '⼜': '又', '⼝': '口', '⼟': '土', '⼠': '士',
    '⼤': '大', '⼥': '女', '⼦': '子', '⼩': '小', '⼭': '山', '⼯': '工',
    '⼰': '己', '⼲': '干', '⼴': '广', '⼿': '手', '⽂': '文',
    '⽃': '斗', '⽇': '日', '⽉': '月', '⽊': '木', '⽌': '止', '⽐': '比',
    '⽑': '毛', '⽒': '氏', '⽔': '水', '⽕': '火', '⽖': '爪', '⽗': '父',
    '⽚': '片', '⽛': '牙', '⽜': '牛', '⽝': '犬', '⽟': '玉', '⽤': '用',
    '⽥': '田', '⽩': '白', '⽪': '皮', '⽬': '目', '⽯': '石', '⽰': '示',
    '⽲': '禾', '⽴': '立', '⽵': '竹', '⽶': '米', '⽷': '糸', '⽸': '缶',
    '⽹': '网', '⽺': '羊', '⽻': '羽', '⽼': '老', '⽽': '而', '⾁': '肉',
    '⾂': '臣', '⾃': '自', '⾄': '至', '⾆': '舌', '⾇': '舛', '⾈': '舟',
    '⾉': '艮', '⾊': '色', '⾍': '虫', '⾎': '血', '⾏': '行', '⾐': '衣',
    '⾒': '见', '⾓': '角', '⾔': '言', '⾕': '谷', '⾖': '豆', '⾗': '豕',
    '⾚': '赤', '⾛': '走', '⾜': '足', '⾝': '身', '⾞': '车', '⾟': '辛',
    '⾠': '辰', '⾢': '邑', '⾣': '酉', '⾥': '里', '⾦': '金', '⾧': '长',
    '⾨': '门', '⾩': '阜', '⾪': '隶', '⾬': '雨', '⾭': '青', '⾮': '非',
    '⾯': '面', '⾰': '革', '⾳': '音', '⾸': '首', '⾹': '香', '⾺': '马',
    '⾻': '骨', '⾼': '高', '⿁': '鬼', '⿂': '鱼', '⿃': '鸟', '⿇': '麻',
    '⿊': '黑', '⿍': '鼎', '⿏': '鼠', '⿐': '鼻', '⿑': '齐', '⿒': '齿',
    '⿓': '龙', '⻔': '门', '⻋': '车', '⻓': '长', '⻄': '西', '⻘': '青',
    '⻚': '页', '⻜': '飞', '⻝': '食', '⻢': '马', '⻤': '鬼', '⻥': '鱼',
    '⻦': '鸟', '⻨': '麦', '⻩': '黄', '⻮': '齿', '⻰': '龙', '⻳': '龟',
}
RADICAL_TABLE = str.maketrans(RADICAL_MAP)

# Page furniture.  Each pattern is anchored enough that ordinary question text
# cannot match it.
NOISE_LINE_RES = [
    re.compile(r'^\s*公众号\s*[:：].*$'),
    re.compile(r'^\s*(?:微信|QQ|微博)\s*(?:公众号|群|号)?\s*[:：].*$'),
    re.compile(r'^\s*打开粉笔APP.*$'),
    re.compile(r'^\s*扫[一\s]*扫.*$'),
    re.compile(r'^\s*第\s*\d+\s*页(?:\s*[,，]\s*共\s*\d+\s*页)?\s*$'),
    re.compile(r'^\s*\d+\s*/\s*\d+\s*$'),
    re.compile(r'^\s*[-—]+\s*\d+\s*[-—]+\s*$'),
    re.compile(r'^\s*\S*\.indd\s*\d*\s*$', re.I),
    re.compile(r'^\s*https?://\S+\s*$'),
    re.compile(r'^\s*www\.\S+\s*$'),
]

# Inline furniture: removed wherever it appears, including mid-line.
NOISE_INLINE_RES = [
    re.compile(r'打开粉笔APP[^。\n]{0,40}'),
    re.compile(r'公众号\s*[:：]\s*\S{1,30}'),
    re.compile(r'\S{0,20}\.indd\s+\d+\s+\d{4}[-/]\d{1,2}[-/]\d{1,2}[^\n]*'),
    re.compile(r'粉笔大数据\s*[:：]\s*本题正确率为\s*[\d.]+%\s*[,，]?\s*易错项为\s*[A-D]\s*[。.]?'),
    re.compile(r'扫[一\s]*扫[^。\n]{0,30}'),
]

# Tails that mean "the next section of the paper started inside this field".
SECTION_TAIL_RE = re.compile(
    r'(?:'
    r'第[一二三四五六七八九十]+部分\s*(?:常识判断|言语理解|数量关系|判断推理|资料分析)'
    r'|[一二三四五六七八九十]\s*[、.．]\s*(?:数学运算|数字推理|图形推理|定义判断|类比推理|逻辑判断|片段阅读|选词填空|语句表达|常识判断|言语理解|数量关系|判断推理|资料分析)'
    r'|【例题】'
    r'|参考答案(?:及解析)?\s*$'
    r')'
)

ANSWER_LEAK_RE = re.compile(r'(?:正确答案\s*[为是]?\s*[:：]?\s*[【\[]?[A-D]|【解析】)')

# "公公务务员员" style bold-face doubling.  Only collapse when a long run of
# consecutive characters is doubled, so ordinary words like 源源不断 survive.
def _collapse_doubling(text: str) -> tuple[str, bool]:
    if len(text) < 8:
        return text, False
    best_start = best_len = 0
    i = 0
    while i + 1 < len(text):
        run = 0
        j = i
        while j + 1 < len(text) and text[j] == text[j + 1] and '一' <= text[j] <= '鿿':
            run += 1
            j += 2
        if run > best_len:
            best_len, best_start = run, i
        i = j + 1 if run else i + 1
    if best_len < 4:
        return text, False
    end = best_start + best_len * 2
    collapsed = text[:best_start] + text[best_start:end:2] + text[end:]
    return collapsed, True


def _clean_text(value: str | None, *, stats: collections.Counter) -> str:
    if not value:
        return ''
    text = unicodedata.normalize('NFKC', value).translate(RADICAL_TABLE)
    text = text.replace('\r\n', '\n').replace('\r', '\n')
    text = text.replace(' ', ' ').replace('　', ' ')
    text = re.sub('[​-‏‪-‮﻿]', '', text)  # zero-width / bidi marks

    kept = []
    for line in text.split('\n'):
        if any(pattern.match(line) for pattern in NOISE_LINE_RES):
            stats['noise_line'] += 1
            continue
        for pattern in NOISE_INLINE_RES:
            new_line, n = pattern.subn('', line)
            if n:
                stats['noise_inline'] += n
                line = new_line
        kept.append(line.strip())
    text = '\n'.join(kept)

    text = re.sub(r'\.{4,}', '……', text)
    text = re.sub(r'。{2,}', '。', text)
    text = re.sub(r'[ \t]{2,}', ' ', text)
    text = re.sub(r'\n{3,}', '\n\n', text)
    collapsed, changed = _collapse_doubling(text)
    if changed:
        stats['collapsed_doubling'] += 1
        text = collapsed
    return text.strip()


def _clean_field(value: str | None, *, stats: collections.Counter, cut_tail: bool) -> str:
    text = _clean_text(value, stats=stats)
    if cut_tail:
        match = SECTION_TAIL_RE.search(text)
        if match and match.start() > 0:
            stats['section_tail_cut'] += 1
            text = text[:match.start()].strip()
    return text


def normalize_objective(row: dict, stats: collections.Counter):
    """Return (record, reject_reason).  Exactly one is None."""
    stem = _clean_field(row.get('question_stem'), stats=stats, cut_tail=True)
    options = [
        _clean_field(row.get('option_' + label), stats=stats, cut_tail=True)
        for label in 'abcd'
    ]
    explanation = _clean_text(row.get('explanation'), stats=stats)
    material = _clean_text(row.get('material'), stats=stats)
    answer = (row.get('answer') or '').strip().upper()

    if not answer or any(ch not in 'ABCD' for ch in answer):
        return None, 'answer_not_abcd'
    if len(set(answer)) != len(answer):
        return None, 'answer_repeats_letter'
    if any(not option for option in options):
        return None, 'option_empty_after_clean'
    if len(re.sub(r'\s+', '', stem)) < 4 and not material:
        return None, 'stem_too_short'
    if ANSWER_LEAK_RE.search(stem) or any(ANSWER_LEAK_RE.search(o) for o in options):
        return None, 'answer_leaked_into_question'
    if any(len(option) > 300 for option in options):
        return None, 'option_too_long'
    if len(stem) > 4000:
        return None, 'stem_too_long'
    if len({o.strip() for o in options}) != 4:
        return None, 'duplicate_options'

    out = dict(row)
    out.update({
        'question_stem': stem,
        'option_a': options[0], 'option_b': options[1],
        'option_c': options[2], 'option_d': options[3],
        'explanation': explanation,
        'material': material,
        'answer': answer,
        'is_multiple': len(answer) > 1,
    })
    return out, None


def normalize_subjective(row: dict, stats: collections.Counter):
    stem = _clean_text(row.get('fr_question_stem'), stats=stats)
    answer = _clean_text(row.get('answer'), stats=stats)
    material = _clean_text(row.get('material'), stats=stats)
    if len(re.sub(r'\s+', '', stem)) < 8:
        return None, 'subjective_stem_too_short'
    if len(re.sub(r'\s+', '', answer)) < 50:
        return None, 'reference_answer_too_short'
    if len(re.sub(r'\s+', '', material)) < 200:
        return None, 'material_too_short'
    out = dict(row)
    out.update({'fr_question_stem': stem, 'answer': answer, 'material': material})
    return out, None


def main() -> int:
    rows = list(read_jsonl(build_path('parsed.jsonl')))
    stats: collections.Counter = collections.Counter()
    rejects: list[dict] = []
    out_rows: list[dict] = []
    seen_codes: dict[str, dict] = {}

    for row in rows:
        kind = row.get('kind')
        if kind == 'paper':
            out_rows.append(row)
            continue
        if kind == 'objective':
            record, reason = normalize_objective(row, stats)
        elif kind == 'subjective':
            record, reason = normalize_subjective(row, stats)
        else:
            continue
        if reason:
            stats['reject_' + reason] += 1
            rejects.append({
                'kind': 'reject', 'stage': 'normalize', 'reason': reason,
                'question_code': row.get('question_code'),
                'source_name': row.get('source_name'),
                'question_no': row.get('question_no'),
                'preview': re.sub(r'\s+', ' ', (row.get('question_stem')
                                                or row.get('fr_question_stem') or ''))[:400],
            })
            continue
        code = record['question_code']
        if code in seen_codes:
            # parse.py must already guarantee uniqueness; keep the evidence
            # instead of silently overwriting the earlier row.
            stats['reject_duplicate_code'] += 1
            rejects.append({
                'kind': 'reject', 'stage': 'normalize', 'reason': 'duplicate_question_code',
                'question_code': code, 'source_name': record.get('source_name'),
                'question_no': record.get('question_no'),
                'preview': re.sub(r'\s+', ' ', record.get('question_stem')
                                  or record.get('fr_question_stem') or '')[:400],
            })
            continue
        seen_codes[code] = record
        out_rows.append(record)
        stats['kept_' + kind] += 1

    write_jsonl(build_path('normalized.jsonl'), out_rows)
    write_jsonl(build_path('normalize_rejects.jsonl'), rejects)

    objective = [r for r in out_rows if r.get('kind') == 'objective']
    subjective = [r for r in out_rows if r.get('kind') == 'subjective']
    rep = io.open(build_path('normalize_report.txt'), 'w', encoding='utf-8')

    def out(*parts):
        rep.write(' '.join(str(p) for p in parts) + '\n')

    out('input rows', len(rows))
    out('kept objective', len(objective))
    out('kept subjective', len(subjective))
    out('kept paper', sum(r.get('kind') == 'paper' for r in out_rows))
    out('rejected', len(rejects))
    out('')
    out('-- cleaning actions --')
    for key in sorted(k for k in stats if not k.startswith('reject_')):
        out(f'  {key}: {stats[key]}')
    out('')
    out('-- normalize rejects --')
    for key in sorted(k for k in stats if k.startswith('reject_')):
        out(f'  {key[7:]}: {stats[key]}')
    out('')
    out('-- objective by question_type --')
    for k, v in collections.Counter(r['question_type'] for r in objective).most_common():
        out(f'  {k}: {v}')
    out('-- objective by source_kind --')
    for k, v in collections.Counter(r['source_kind'] for r in objective).most_common():
        out(f'  {k}: {v}')
    out('-- multiple choice rows --', sum(1 for r in objective if r.get('is_multiple')))
    out('-- rows carrying shared material --',
        sum(1 for r in objective if (r.get('material') or '').strip()))
    out('-- subjective answer length --')
    lens = sorted(len(re.sub(r'\s+', '', r['answer'])) for r in subjective)
    if lens:
        out('   min=%d p50=%d max=%d  (all must be >50)' % (lens[0], lens[len(lens) // 2], lens[-1]))
    rep.close()

    # Fixed-seed sample for human verification against the source PDFs.
    rnd = random.Random(20260921)
    pool = list(objective)
    rnd.shuffle(pool)
    picked: list[dict] = []
    for kind in ('gk', 'changshi_660', 'xingce_5000'):
        picked.extend([r for r in pool if r['source_kind'] == kind][:5])
    picked.extend([r for r in pool if r.get('is_multiple')][:3])
    picked.extend([r for r in pool if (r.get('material') or '').strip()][:2])
    seen = set()
    sample = []
    for row in picked:
        if row['question_code'] not in seen:
            seen.add(row['question_code'])
            sample.append(row)
    smp = io.open(build_path('sample_20.txt'), 'w', encoding='utf-8')
    smp.write('Fixed seed 20260921 sample for manual verification against source PDFs.\n')
    for row in sample[:20]:
        smp.write('\n' + '=' * 78 + '\n')
        smp.write('code=%s\nsource=%s  no=%s\ntype=%s  answer=%s  multi=%s\n' % (
            row['question_code'], row['source_name'], row['question_no'],
            row['question_type'], row['answer'], row.get('is_multiple')))
        if row.get('material'):
            smp.write('MATERIAL: %s\n' % row['material'][:600])
        smp.write('STEM: %s\n' % row['question_stem'])
        for label in 'abcd':
            smp.write('  %s. %s\n' % (label.upper(), row['option_' + label]))
        smp.write('EXPL: %s\n' % (row.get('explanation') or '')[:500])
    smp.close()

    progress('normalize done objective=%d subjective=%d rejects=%d'
             % (len(objective), len(subjective), len(rejects)))
    progress('-> ' + build_path('normalized.jsonl'))
    progress('-> ' + build_path('normalize_report.txt'))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
