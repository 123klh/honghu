"""Stage 2: parse extracted PDF text into importer JSONL records.

The parser deliberately keeps source metadata on every row.  ``build/parsed.jsonl``
contains ``paper``, ``objective`` and ``subjective`` records; rows that cannot pass
the structural checks are written to ``build/rejects.jsonl`` instead of being
silently discarded.

The default command only consumes ``build/extract_index.jsonl``.  ``--include-extras``
also discovers the 660-item common-knowledge PDF and the 5000-question books and
extracts them into the same cache (this can take several minutes on first run).
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import re
import unicodedata
from difflib import SequenceMatcher
from collections import defaultdict
from typing import Iterable, Iterator

from common import (
    BUILD_DIR,
    DIR_XINGCE_5000,
    SOURCE_ROOT,
    build_path,
    list_pdfs,
    paper_key,
    progress,
    read_jsonl,
)


# A few PDF producers emit Kangxi radicals instead of the normal Han character.
# This is intentionally small: normalize.py owns the full text normalization pass.
RADICAL_MAP = str.maketrans({
    '⼀': '一', '⼆': '二', '⼈': '人', '⼗': '十', '⼤': '大',
    '⼩': '小', '⼭': '山', '⼯': '工', '⼼': '心', '⽤': '用',
    '⾏': '行', '⾼': '高', '⻔': '门', '⻋': '车', '⻓': '长',
    '⽹': '网', '⽇': '日', '⽉': '月', '⽂': '文', '⽣': '生',
})

QUESTION_START_RE = re.compile(
    r'(?m)^\s*(?P<num>\d{1,3})\s*[\.．、]\s*'
)

# Objective answer books use both "第【1】题" and a bare "1." heading.
ANSWER_START_RE = re.compile(
    r'(?m)^\s*(?:第\s*[【\[]?\s*(?P<n1>\d{1,3})\s*[】\]]?\s*题|'
    r'(?P<n2>\d{1,3})\s*[\.．、])'
)

OPTION_MARK_RE = re.compile(r'(?<![A-Za-z0-9])(?P<label>[ABCD])\s*[\.．、:：]\s*')

ANSWER_PATTERNS = (
    # 2023 style: 正确答案:【B】 / 正确答案是：【B】
    re.compile(r'正确答案\s*(?:为|是)?\s*[:：]?\s*[【\[]?\s*([A-D](?:\s*[、，,/]\s*[A-D])*)'),
    # 2019/2022 style: 因此，选择X选项。/故正确答案为X。
    re.compile(r'(?:因此|所以|故)?\s*[,，。]?\s*选择\s*([A-D](?:\s*[、，,/]\s*[A-D])*)\s*选项'),
    re.compile(r'答案\s*[】\]]?\s*[:：]?\s*([A-D](?:\s*[、，,/]\s*[A-D])*)'),
)

SECTION_PATTERNS = (
    ('常识判断', re.compile(r'常识判断')),
    ('言语理解与表达', re.compile(r'言语理解(?:与表达)?')),
    ('数量关系', re.compile(r'数量关系')),
    ('判断推理', re.compile(r'判断推理')),
    ('资料分析', re.compile(r'资料分析')),
)

CN_NUM = {'一': 1, '二': 2, '三': 3, '四': 4, '五': 5,
          '六': 6, '七': 7, '八': 8, '九': 9, '十': 10}


def _nfkc(text: str) -> str:
    text = unicodedata.normalize('NFKC', text or '')
    return text.translate(RADICAL_MAP).replace('\r\n', '\n').replace('\r', '\n')


def _source_id(path: str) -> str:
    raw = os.path.normcase(os.path.abspath(path)).encode('utf-8', 'replace')
    return hashlib.sha1(raw).hexdigest()[:20]


def _paper_parts(name: str):
    key = paper_key(name)
    if key is None:
        # A handful of old filenames contain a typo such as "200年".  Do not
        # invent a year; the caller will put the row in rejects.
        return None
    year, ptype = key
    return year, ptype, f'{year}:{ptype}'


def _strip_noise(text: str) -> str:
    """Remove repeated page furniture while retaining paragraph boundaries."""
    out = []
    for raw in _nfkc(text).split('\n'):
        line = raw.strip()
        if not line:
            if out and out[-1] != '':
                out.append('')
            continue
        if re.fullmatch(r'[-—·\s]*第\s*\d+\s*页(?:[，,]\s*共\s*\d+\s*页)?[-—·\s]*', line):
            continue
        if re.fullmatch(r'[-—·\s]*\d+\s*/\s*\d+[-—·\s]*', line):
            continue
        if re.fullmatch(r'[-—·\s]*\d+\s*[-—·]\s*', line):
            continue
        # In the 5000 books these are printing artefacts, never question data.
        if re.search(r'\.?i+n+d+', line, re.I):
            continue
        line = re.sub(r'打开粉笔APP.*|扫一扫.*', '', line, flags=re.I).strip()
        if re.match(r'决战行测5000题|第[一二三四五六七八九十]+[章节]|本部分题目解析见', line):
            continue
        out.append(line)
    while out and out[0] == '':
        out.pop(0)
    while out and out[-1] == '':
        out.pop()
    return '\n'.join(out)


def _read_cache(row: dict) -> str:
    path = os.path.join(BUILD_DIR, 'text_cache', row['cache'])
    with io.open(path, encoding='utf-8') as fh:
        data = json.load(fh)
    pages = []
    for page in data.get('pages') or []:
        lines = page.splitlines()
        lines = [line for i,line in enumerate(lines)
                 if not (i >= len(lines)-3 and re.fullmatch(r'\s*\d{1,3}\s*',line))]
        pages.append('\n'.join(lines))
    return _strip_noise('\n'.join(pages))


def _answer_token(value: str | None) -> str | None:
    if not value:
        return None
    letters = []
    for char in value.upper():
        if char in 'ABCD' and char not in letters:
            letters.append(char)
    return ''.join(letters) or None


def _section_at(text: str, position: int) -> str:
    best = None
    best_pos = -1
    for name, pattern in SECTION_PATTERNS:
        for match in pattern.finditer(text, 0, position):
            if match.start() > best_pos:
                best = name
                best_pos = match.start()
    return best or '常识判断'


def _longest_increasing(numbers: list[int]) -> list[int]:
    """Indices of a longest strictly increasing subsequence (patience sort).

    Question numbering inside one paper only ever goes up.  A wrapped decimal
    such as "该电线杆高1.\n8米" makes ``1.`` look like a question heading, which
    used to overwrite question 1 and produce duplicate ``question_code`` values.
    Keeping the longest increasing run drops those strays without trusting a
    greedy left-to-right scan (a single stray high number would otherwise
    swallow every real heading after it).
    """
    if not numbers:
        return []
    tails: list[int] = []          # tails[k] = index of smallest tail, run length k+1
    prev: list[int | None] = [None] * len(numbers)
    for i, value in enumerate(numbers):
        lo, hi = 0, len(tails)
        while lo < hi:
            mid = (lo + hi) // 2
            if numbers[tails[mid]] < value:
                lo = mid + 1
            else:
                hi = mid
        prev[i] = tails[lo - 1] if lo > 0 else None
        if lo == len(tails):
            tails.append(i)
        else:
            tails[lo] = i
    out = []
    cursor = tails[-1]
    while cursor is not None:
        out.append(cursor)
        cursor = prev[cursor]
    return out[::-1]


def _question_blocks(text: str, *, monotonic: bool = True) -> Iterator[tuple[int, str]]:
    matches = list(QUESTION_START_RE.finditer(text))
    if monotonic and matches:
        keep = set(_longest_increasing([int(m.group('num')) for m in matches]))
        matches = [m for i, m in enumerate(matches) if i in keep]
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        number = int(match.group('num'))
        block = text[match.end():end].strip()
        if block:
            yield number, block


TAIL_BOUNDARY_RE = re.compile(
    r'(?m)^\s*(?:'
    r'第[一二三四五六七八九十]+部分'
    r'|[一二三四五六七八九十]+\s*[、.．]\s*(?:常识判断|言语理解|数量关系|判断推理|资料分析|数学运算|数字推理|图形推理|定义判断|类比推理|逻辑判断|逻辑推理|片段阅读|选词填空|语句表达)'
    r'|[（(]\s*[一二三四五六七八九十]+\s*[）)]'
    r'|(?:根据|请根据)(?:以下|下列|所给|下面)'
    r'|参考答案'
    r'|【解析】'
    r')'
)


def _trim_option_tail(value: str) -> str:
    """Cut section headers and shared-material text off the final option.

    In the real-paper PDFs the text that follows option D on the page (a part
    heading, or the material block introducing the next question group) lands
    inside option D, which is how one option reached 7,776 characters.
    """
    match = TAIL_BOUNDARY_RE.search(value)
    if match and match.start() > 0:
        value = value[:match.start()]
    return value.strip(' \t\n;；')


def _split_options(block: str):
    """Return stem and A-D values using the last valid ordered label sequence."""
    marks = list(OPTION_MARK_RE.finditer(block))
    candidates = []
    for i, first in enumerate(marks):
        if first.group('label') != 'A':
            continue
        labels = []
        cursor = i
        for expected in 'ABCD':
            while cursor < len(marks) and marks[cursor].group('label') != expected:
                cursor += 1
            if cursor >= len(marks):
                break
            labels.append(marks[cursor])
            cursor += 1
        if len(labels) != 4:
            continue
        values = []
        for j, mark in enumerate(labels):
            stop = labels[j + 1].start() if j < 3 else len(block)
            value = block[mark.end():stop].strip(' \t\n;；')
            if j == 3:
                value = _trim_option_tail(value)
            if not value:
                break
            values.append(value)
        if len(values) == 4:
            candidates.append((first.start(), block[:first.start()].strip(), values))
    if not candidates:
        return None
    _, stem, values = candidates[-1]
    return stem, values


MATERIAL_GROUP_RE = re.compile(
    r'(?m)^\s*(?:[（(]\s*[一二三四五六七八九十]+\s*[）)]|'
    r'(?:根据|请根据)(?:以下|下列|所给|下面)[^\n]{0,40})\s*'
)

# Figures and tables never survive text extraction.  A question that depends on
# one is unusable no matter how clean its options look.
FIGURE_DEPENDENCY_RE = re.compile(
    r'如[下右左]?图|下图|上图|右图|左图|图中|图形|所示图|饼形图|饼图|柱状图|折线图|条形图'
    r'|统计图|示意图|下表|上表|如表|表中|见图'
)


def _shared_materials(text: str) -> dict[int, str]:
    """Map question number -> shared material block (资料分析 / 阅读材料 groups).

    The material sits between a group marker such as ``(一)`` and the first
    numbered question of that group; every question up to the next marker
    shares it.  Without this, 资料分析 items reach the database as unanswerable
    stems like "表中'?'处的数值应为".
    """
    out: dict[int, str] = {}
    markers = list(MATERIAL_GROUP_RE.finditer(text))
    for index, marker in enumerate(markers):
        end = markers[index + 1].start() if index + 1 < len(markers) else len(text)
        segment = text[marker.end():end]
        first = QUESTION_START_RE.search(segment)
        if not first:
            continue
        material = segment[:first.start()].strip()
        if len(re.sub(r'\s+', '', material)) < 40:
            continue
        numbers = [int(m.group('num')) for m in QUESTION_START_RE.finditer(segment)]
        keep = _longest_increasing(numbers)
        for i in keep:
            out[numbers[i]] = material
    return out


def _parse_answer_blocks(text: str) -> dict[int, dict]:
    answer_rows: dict[int, dict] = {}
    matches = []
    last_number = 0
    for candidate in ANSWER_START_RE.finditer(text):
        number = int(candidate.group('n1') or candidate.group('n2'))
        # Decimal values and numbered subpoints must not overwrite earlier answers.
        if number > last_number and number <= 150:
            matches.append(candidate)
            last_number = number
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        number = int(match.group('n1') or match.group('n2'))
        block = text[match.end():end].strip()
        direct = re.match(r'\s*([A-D]+)\s*[。.]?\s*(?=【解析】)', block)
        answer = _answer_token(direct.group(1) if direct else None)
        marker_end = direct.end() if direct else 0
        if answer is None:
            # Prefer explicit answer markers near the end of a block.  The
            # explanatory text can contain many ordinary "A项" references.
            for pattern in ANSWER_PATTERNS:
                found = list(pattern.finditer(block))
                if found:
                    hit = found[-1]
                    answer = _answer_token(hit.group(1))
                    marker_end = hit.end()
                    break
        if answer is None:
            continue
        explanation = block.strip()
        explanation = re.sub(r'^\s*[【\[]?解析[】\]]?\s*', '', explanation)
        answer_rows[number] = {'answer': answer, 'explanation': explanation}
    return answer_rows


def _parse_inline_objective(text: str, *, group: str):
    """Parse self-contained 660/5000 answer blocks (question + options + answer)."""
    rows = []
    for number, block in _question_blocks(text):
        marker = re.search(r'【?答案\s*[】\]]\s*([A-D]+(?:\s*[、，,/]\s*[A-D]+)*)', block)
        split = _split_options(block[:marker.start()] if marker else block)
        if not split:
            continue
        stem, options = split
        answer = None
        explanation = ''
        if marker:
            answer = _answer_token(marker.group(1))
            explanation = block[marker.end():].strip()
        if not answer:
            # 5000 question books contain no answers; they are paired later
            # with their lower-volume answer books by sequence.
            answer_rows = _parse_answer_blocks(block)
            if answer_rows:
                answer = next(iter(answer_rows.values()))['answer']
        rows.append((number, stem, options, answer, explanation))
    return rows


def _objective_record(row: dict, paper, number: int, stem: str, options, answer: str,
                      explanation: str, question_type: str, source_kind: str = 'gk',
                      material: str = ''):
    return {
        'kind': 'objective',
        'question_code': f'obj:{_source_id(row["source_path"])}:{number}',
        'subject': '行测',
        'question_stem': stem,
        'option_a': options[0], 'option_b': options[1],
        'option_c': options[2], 'option_d': options[3],
        'answer': answer,
        'explanation': explanation,
        'question_type': question_type,
        'material': material,
        'paper_key': paper['paper_key'],
        'paper_name': paper['paper_name'],
        'source_path': row['source_path'],
        'source_name': row['source_name'],
        'source_kind': source_kind,
        'question_no': number,
    }


def _paper_record(row: dict, subject: str, source: str, key_parts):
    year, ptype, raw_key = key_parts
    paper_key_text = f'{subject}:{raw_key}'
    return {
        'kind': 'paper',
        'paper_key': paper_key_text,
        'paper_name': row['source_name'],
        'source': source,
        'debut': str(year),
        'subject': subject,
        'paper_type': ptype,
        'source_path': row['source_path'],
        'source_name': row['source_name'],
    }


def _reject(row: dict, reason: str, number: int | None = None, preview: str = ''):
    return {
        'kind': 'reject',
        'reason': reason,
        'question_no': number,
        'source_path': row.get('source_path'),
        'source_name': row.get('source_name'),
        'preview': re.sub(r'\s+', ' ', preview)[:600],
    }


def _choose_rows(rows: list[dict], groups: set[str]) -> dict[tuple, dict]:
    grouped: dict[tuple, list[dict]] = defaultdict(list)
    for row in rows:
        if row.get('group') not in groups or not row.get('chars'):
            continue
        parts = _paper_parts(row.get('source_name', ''))
        if parts:
            grouped[(parts[0], parts[1])].append(row)
    chosen = {}
    for key, candidates in grouped.items():
        # The curated 2011-2025 directory is preferred over the older mirror;
        # within one group the largest text is generally the complete version.
        candidates.sort(key=lambda r: (r.get('chars', 0) > 1000,
                                      r.get('group') == 'gk_xingce_stem_alt',
                                      r.get('chars', 0)), reverse=True)
        chosen[key] = candidates[0]
    return chosen


def _parse_objective_sources(index_rows: list[dict], records: list[dict], rejects: list[dict]):
    stems = _choose_rows(index_rows, {'gk_xingce_stem', 'gk_xingce_stem_alt'})
    answers = _choose_rows(index_rows, {'gk_xingce_answer'})
    answer_cache: dict[tuple, dict[int, dict]] = {}
    for key, row in answers.items():
        try:
            answer_cache[key] = _parse_answer_blocks(_read_cache(row))
        except Exception as exc:
            rejects.append(_reject(row, f'answer_extract:{type(exc).__name__}'))
    for key, row in stems.items():
        parts = _paper_parts(row.get('source_name', ''))
        if not parts:
            rejects.append(_reject(row, 'paper_key_missing'))
            continue
        paper = _paper_record(row, '行测', '国考行测真题', parts)
        records.append(paper)
        try:
            text = _read_cache(row)
        except Exception as exc:
            rejects.append(_reject(row, f'stem_extract:{type(exc).__name__}'))
            continue
        answers_for_paper = answer_cache.get(key, {})
        materials = _shared_materials(text)
        for number, block in _question_blocks(text):
            split = _split_options(block)
            if not split:
                # A bare number in an article/heading is not an objective item.
                continue
            stem, options = split
            preview = stem
            if re.search(r'题目正在全力以赴征集|选项均?缺失|A\.缺失', block):
                rejects.append(_reject(row, 'placeholder', number, preview))
                continue
            answer_row = answers_for_paper.get(number)
            if not answer_row:
                rejects.append(_reject(row, 'missing_answer', number, preview))
                continue
            answer = _answer_token(answer_row.get('answer'))
            if not answer or any(letter not in 'ABCD' for letter in answer):
                rejects.append(_reject(row, 'invalid_answer', number, preview))
                continue
            if any(not value.strip() for value in options) or len(options) != 4:
                rejects.append(_reject(row, 'invalid_options', number, preview))
                continue
            question_type = _section_at(text, text.find(block))
            # Shared material is only a real thing in 资料分析.  Attaching the
            # nearest preceding paragraph anywhere else glued unrelated reading
            # passages onto 数量关系 items.
            material = materials.get(number, '') if question_type == '资料分析' else ''
            if FIGURE_DEPENDENCY_RE.search(stem) or FIGURE_DEPENDENCY_RE.search(material):
                # The figure itself is not in the extracted text, so the question
                # cannot be answered by a user.  Reject instead of shipping it.
                rejects.append(_reject(row, 'figure_not_extractable', number, preview))
                continue
            if len(re.sub(r'\s+', '', stem)) < 6 and not material:
                rejects.append(_reject(row, 'stem_too_short', number, preview))
                continue
            if question_type == '资料分析' and not material:
                # Unanswerable without the table/passage it refers to.
                rejects.append(_reject(row, 'shared_material_missing', number, preview))
                continue
            records.append(_objective_record(
                row, paper, number, stem, options, answer,
                answer_row.get('explanation', ''), question_type,
                material=material,
            ))


def _ordinal(value: str) -> int | None:
    if value.isdigit():
        return int(value)
    if value in CN_NUM:
        return CN_NUM[value]
    if value == '十一':
        return 11
    return None


REQUIREMENT_START_RE = re.compile(
    r'(?m)^\s*(?P<n>\d{1,2}|[一二三四五六七八九十]{1,3})\s*[、．\.]\s*'
)

# --- shenlun structure -------------------------------------------------------
#
# Question stems ("作答要求") and their reference answers live in two separate
# regions of the same PDF, each with its own numbering.  Both regions are
# parsed independently and then aligned, because between 2000 and 2024 the
# publishers used at least six numbering styles and two of the files ship
# *two* competing answer sets.

_SL_NUM = r'\d{1,2}|[一二三四五六七八九十]{1,3}'
# "一、"  "1."  "1、"  "(一)"  "（1）"  "第一题"  -- parenthesised forms carry no
# trailing punctuation, bare forms always do.
_SL_PREFIX = (
    r'(?:'
    rf'[（(][ \t]*(?P<np>{_SL_NUM})[ \t]*[）)][ \t]*[、.．]?'
    rf'|第[ \t]*(?P<nt>{_SL_NUM})[ \t]*道?[题问][ \t]*(?:的)?[ \t]*[、.．：:]?'
    rf'|(?P<nb>{_SL_NUM})[ \t]*[、.．]'
    r'|问题[ \t]*(?P<nq>' + _SL_NUM + r')[ \t]*[、.．：:]?'
    r')[ \t]*'
)
# "参考答案" but never "参考答案说明", which is editorial commentary on the answer.
# Some files carry two competing answer sets interleaved per question, tagged
# "(H)" / "(F)".  The tag is captured so only one set is kept.
_SL_ANSWER_WORD = (
    r'【?(?:参考答案|答案提示|答案要点|参考例文|参考范文)】?'
    r'(?![^\n]{0,4}说明)[ \t]*(?:[（(](?P<ver>[A-Z])[）)])?[ \t]*[:：]?'
)

SL_ANSWER_RE = re.compile(rf'(?m)^[ \t]*(?:{_SL_PREFIX})?{_SL_ANSWER_WORD}[ \t]*$')
SL_ANSWER_LOOSE_RE = re.compile(rf'(?m)^[ \t]*(?:{_SL_PREFIX})?{_SL_ANSWER_WORD}')
SL_REQ_RE = re.compile(rf'(?m)^[ \t]*{_SL_PREFIX}')
# Real questions always state their point value; nested "要求" items never do.
SL_SCORE_RE = re.compile(r'[（(]\s*(?:满分)?\s*(\d{1,3})\s*分\s*[）)]')
# A second, competing answer set appended after the first one.
# A whole second answer set appended after the first.  Interleaved per-question
# variants are handled by _drop_alternate_versions instead.
SL_SECOND_VERSION_RE = re.compile(r'第二(?:版本|套答案)')
SL_REQ_HEADER_RE = re.compile(
    r'(?m)^\s*(?:[一二三四五六七八九十]{1,3}\s*[、．.]\s*)?(?:申论)?(?:作答要求|答题要求|申论要求)\s*$'
)


def _sl_num(match) -> str:
    """Marker number tagged with its numeral system, e.g. 'c1' or 'a1'.

    The tag matters: papers that nest sub-questions under a group header use
    "(一)" for the header and "(1)/(2)" for the questions underneath it.
    Folding 一 and 1 to the same key makes the header shadow question 1, which
    silently pairs a real answer with a stem that contains no question.
    """
    token = (match.group('np') or match.group('nt')
             or match.group('nq') or match.group('nb') or '')
    value = _ordinal(token)
    if value is None:
        return ''
    return ('a' if token.isdigit() else 'c') + str(value)


def _sl_ordinal(number: str) -> int | None:
    return int(number[1:]) if number[1:].isdigit() else None


# A reference answer ends where a competing version or the teaching
# walkthrough begins; neither is part of the answer itself.
SL_ANSWER_TAIL_RE = re.compile(
    r'(?m)^\s*(?:'
    r'参考答案[二三四](?![^\n]{0,6}要点)'
    r'|参考答案说明'
    r'|第[一二三四五]步\s*[—–-]{0,3}'
    r'|【?(?:解析|思路点拨|答题思路|审题)】?\s*[:：]?\s*$'
    r')'
)


def _trim_answer(text: str) -> str:
    match = SL_ANSWER_TAIL_RE.search(text)
    if match and match.start() > 0:
        text = text[:match.start()]
    # "参考答案一:" heads the first of several variants; it is a label, not content.
    text = re.sub(r'^\s*参考答案[一二三四]?\s*[:：]?\s*', '', text)
    # Some vendor PDFs stamp a sales line into the answer body.
    text = re.sub(r'(?m)^.*(?:加微信|公众号|祝您一飞上岸|购买更多).*$', '', text)
    return text.strip()


def _find_requirement_start(text: str) -> int | None:
    ref = text.find('参考答案')
    limit = ref if ref >= 0 else len(text)
    candidates = [m.start() for m in SL_REQ_HEADER_RE.finditer(text, 0, limit)]
    if candidates:
        return max(candidates)
    # Some 2022-2024 files drop the header line entirely.  Fall back to the
    # first scored question marker that sits in the last third of the paper,
    # which is where the requirements always are.
    for match in SL_REQ_RE.finditer(text, len(text) // 3, limit):
        tail = text[match.end():match.end() + 400]
        if SL_SCORE_RE.search(tail) and re.search(r'请|谈谈|概括|写一篇|结合', tail):
            return match.start()
    return None


def _sl_segments(text: str, matches: list):
    """(number, start, body) per match, body running to the next match."""
    out = []
    for i, match in enumerate(matches):
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        out.append((_sl_num(match), match.start(), text[match.end():end]))
    return out


def _sl_version(match) -> str:
    try:
        return match.group('ver') or ''
    except IndexError:
        return ''


def _drop_alternate_versions(matches: list) -> list:
    """Keep one answer set when two are interleaved per question ((H) vs (F))."""
    tags = [_sl_version(m) for m in matches]
    distinct = {tag for tag in tags if tag}
    if len(distinct) < 2:
        return matches
    keep = tags[next(i for i, tag in enumerate(tags) if tag)]
    return [m for m, tag in zip(matches, tags) if tag in ('', keep)]


def _answer_section_start(text: str, req_start: int | None) -> int:
    """Where the solutions begin: the '(解析)' / '参考答案' banner after the stems."""
    origin = req_start or 0
    for match in re.finditer(r'(?m)^.*[（(]\s*解析\s*[）)].*$', text):
        if match.start() >= origin:
            return match.end()
    match = SL_ANSWER_LOOSE_RE.search(text, origin)
    return match.start() if match else -1


def _requirement_items(req_text: str) -> list[dict]:
    """Scored question stems, in order.  Unscored markers are nested 要求 items."""
    matches = list(SL_REQ_RE.finditer(req_text))
    items = []
    for number, start, body in _sl_segments(req_text, matches):
        score = SL_SCORE_RE.search(body)
        if not score or not number:
            continue
        items.append({'num': number, 'start': start, 'points': int(score.group(1))})
    for pos, item in enumerate(items):
        end = items[pos + 1]['start'] if pos + 1 < len(items) else len(req_text)
        stem = req_text[item['start']:end].strip()
        # The last stem runs to the end of the requirement region, which can
        # include the banner line that introduces the solutions.
        stem = re.sub(r'(?m)^.*[（(]\s*解析\s*[）)].*$', '', stem)
        stem = re.sub(r'(?m)^\s*\d{4}\s*年.*(?:真题|试卷|卷)\s*$', '', stem)
        item['stem'] = stem.strip()
    return items


def _answer_items(ans_text: str) -> list[dict]:
    """Reference answers, in order, from the first answer set only."""
    second = SL_SECOND_VERSION_RE.search(ans_text)
    if second:
        ans_text = ans_text[:second.start()]
    matches = list(SL_ANSWER_RE.finditer(ans_text))
    if len(matches) < 2:
        matches = list(SL_ANSWER_LOOSE_RE.finditer(ans_text))
    matches = _drop_alternate_versions(matches)
    items = []
    for number, _, body in _sl_segments(ans_text, matches):
        body = _trim_answer(body)
        if len(re.sub(r'\s+', '', body)) >= 30:
            items.append({'num': number, 'text': body})
    return items


def _is_clean_run(items: list[dict]) -> bool:
    """True when the numbers run 1..N in one style -- no nesting, none missing."""
    if not items:
        return False
    ordinals = [_sl_ordinal(i['num']) for i in items]
    return ordinals == list(range(1, len(items) + 1))


def _align_shenlun(reqs: list[dict], answers: list[dict]) -> list[tuple[dict, dict]]:
    if not reqs or not answers:
        return []
    numbered = [a for a in answers if a['num']]
    if len(numbered) == len(answers):
        # Walk the answer numbers through the requirement list.  This is what
        # handles papers whose stems nest sub-questions under a group header:
        # the answer side only ever numbers the leaves.
        pairs = []
        cursor = 0
        for answer in answers:
            while cursor < len(reqs) and reqs[cursor]['num'] != answer['num']:
                cursor += 1
            if cursor >= len(reqs):
                break
            pairs.append((reqs[cursor], answer))
            cursor += 1
        if len(pairs) == len(answers):
            return pairs
    # Unnumbered or mismatched: only trust position when both sides are a
    # complete 1..N run of the same length.
    if len(reqs) == len(answers) and _is_clean_run(reqs):
        return list(zip(reqs, answers))
    if len(reqs) == len(answers) and not numbered:
        return list(zip(reqs, answers))
    return []


def _subjective_type(stem: str) -> str:
    if re.search(r'文章|大作文|自拟题目|写一篇', stem):
        return '大作文'
    if re.search(r'宣传|讲话|通知|倡议|公开信|草拟|方案|发言', stem):
        return '应用文'
    if re.search(r'对策|建议|措施|解决', stem):
        return '提出对策'
    if re.search(r'启示|经验|做法', stem):
        return '启示分析'
    if re.search(r'概括|归纳|总结|梳理', stem):
        return '归纳概括'
    return '综合分析'


def _parse_subjective_sources(index_rows: list[dict], records: list[dict], rejects: list[dict]):
    grouped: dict[tuple, list[dict]] = defaultdict(list)
    for row in index_rows:
        if row.get('group') not in {'gk_shenlun', 'gk_shenlun_alt'} or not row.get('chars'):
            continue
        parts = _paper_parts(row.get('source_name', ''))
        if parts:
            grouped[(parts[0], parts[1])].append(row)
    for key, candidates in grouped.items():
        # Combined "题及答案" documents are generally longer than question-only
        # mirrors, so select the one with the most extracted text.
        row = max(candidates, key=lambda r: r.get('chars', 0))
        parts = _paper_parts(row['source_name'])
        try:
            text = _read_cache(row)
        except Exception as exc:
            rejects.append(_reject(row, f'shenlun_extract:{type(exc).__name__}'))
            continue
        req_start = _find_requirement_start(text)
        if req_start is None:
            rejects.append(_reject(row, 'requirement_section_missing'))
            continue
        ans_start = _answer_section_start(text, req_start)
        if ans_start < 0:
            rejects.append(_reject(row, 'answer_section_missing'))
            continue
        # Drop the header line itself so it cannot be mistaken for question 1.
        req_text = text[req_start:ans_start].split('\n', 1)[-1]
        requirements = _requirement_items(req_text)
        answers = _answer_items(text[ans_start:])
        pairs = _align_shenlun(requirements, answers)
        paper = _paper_record(row, '申论', '国考申论真题', parts)
        records.append(paper)
        if not pairs:
            rejects.append(_reject(
                row, 'shenlun_alignment_failed:req=%d/ans=%d' % (
                    len(requirements), len(answers))))
            continue
        material_match = re.search(r'(?m)^\s*(?:二[、.]\s*)?(?:给定)?材料', text)
        material = (text[material_match.start():req_start].strip()
                    if material_match else text[:req_start].strip())
        for position, (requirement, answer) in enumerate(pairs, start=1):
            stem = requirement['stem']
            if len(re.sub(r'\s+', '', answer['text'])) < 30:
                rejects.append(_reject(row, 'missing_subjective_answer', position, stem))
                continue
            records.append({
                'kind': 'subjective',
                # Sequential position, not the printed number: the printed
                # styles collide across papers (一/1/(1)) while position does not.
                'question_code': f'fr:{_source_id(row["source_path"])}:{position}',
                'subject': '申论',
                'fr_question_stem': stem,
                'material': material,
                'answer': answer['text'],
                'fr_question_type': _subjective_type(stem),
                'points': requirement['points'],
                'paper_key': paper['paper_key'],
                'paper_name': paper['paper_name'],
                'source_path': row['source_path'],
                'source_name': row['source_name'],
                'question_no': position,
            })


def _discover_extra_rows(existing: list[dict]):
    """Extract optional 660/5000 PDFs and return transient index rows."""
    from extract import cache_key, extract_text

    known = {os.path.normcase(r.get('source_path', '')) for r in existing}
    paths: list[tuple[str, str]] = []
    for name in os.listdir(SOURCE_ROOT):
        if name.lower().endswith('.pdf') and '660' in name and '常识' in name:
            paths.append(('changshi_660', os.path.join(SOURCE_ROOT, name)))
    if os.path.isdir(DIR_XINGCE_5000):
        for root, _, files in os.walk(DIR_XINGCE_5000):
            for name in files:
                if name.lower().endswith('.pdf'):
                    paths.append(('xingce_5000', os.path.join(root, name)))
    rows = list(existing)
    for group, path in paths:
        if os.path.getsize(path) > 100 * 1024 * 1024:
            continue
        if os.path.normcase(path) in known:
            continue
        try:
            data = extract_text(path)
        except Exception as exc:
            rows.append({'group': group, 'source_path': path, 'source_name': os.path.basename(path),
                         'chars': 0, 'error': f'{type(exc).__name__}: {exc}'})
            continue
        rows.append({'group': group, 'source_path': path, 'source_name': data['source_name'],
                     'page_count': data['page_count'], 'chars': data['chars'],
                     'cache': cache_key(path) + '.json'})
    return rows


def _parse_extra_sources(rows: list[dict], records: list[dict], rejects: list[dict]):
    extra = [r for r in rows if r.get('group') in {'changshi_660', 'xingce_5000'} and r.get('chars')]
    for row in extra:
        try:
            text = _read_cache(row)
        except Exception as exc:
            rejects.append(_reject(row, f'extra_extract:{type(exc).__name__}'))
            continue
        group = row['group']
        if group == 'changshi_660':
            # Only parse blocks under the explicit exercise heading; article lists
            # elsewhere in the book are not questions.
            sections = []
            practice = list(re.finditer(r'课后练习】?', text))
            for i, match in enumerate(practice):
                end = practice[i + 1].start() if i + 1 < len(practice) else len(text)
                next_article = text.find('常识】', match.end(), end)
                sections.append(text[match.end():(next_article if next_article >= 0 else end)])
            parsed = []
            for section in sections:
                parsed.extend(_parse_inline_objective(section, group=group))
            parts = (2024, '660', '2024:660')
            paper = _paper_record(row, '行测', '常识660篇', parts)
            records.append(paper)
            for index, (number, stem, options, answer, explanation) in enumerate(parsed, 1):
                if not answer:
                    rejects.append(_reject(row, 'missing_answer', number, stem))
                    continue
                if FIGURE_DEPENDENCY_RE.search(stem):
                    rejects.append(_reject(row, 'figure_not_extractable', number, stem))
                    continue
                records.append(_objective_record(row, paper, index, stem, options, answer,
                                                 re.sub(r'^\s*[。.、,，]\s*', '', explanation),
                                                 '常识判断', 'changshi_660'))
            continue

        # 5000 books: the upper volume is the question book, lower volume is the
        # answer/explanation book.  We parse self-contained question blocks here;
        # sequence pairing is completed below per subject/volume pair.
        if '上册' not in row['source_name']:
            continue
        starts = list(re.finditer(r'(?m)^\s*(\d{1,3})\s*\.\s*(?=\(\s*(?:19|20)\d{2})', text))
        parsed = []
        for i, match in enumerate(starts):
            end = starts[i+1].start() if i+1 < len(starts) else len(text)
            block = text[match.end():end]
            split = _split_options(block)
            parsed.append((int(match.group(1)), split, block))
        if not parsed:
            continue
        # Keep unpaired question blocks in a transient record; pair below.
        row['_question_items'] = parsed

    # Pair 5000 upper/lower volumes by the normalized book title and sequence.
    uppers = [r for r in extra if r.get('_question_items')]
    for upper in uppers:
        stem_name = re.sub(r'[（(]上册[）)]', '', upper['source_name'])
        candidates = [r for r in extra if '下册' in r.get('source_name', '') and
                      re.sub(r'[（(]下册[）)]', '', r['source_name']) == stem_name]
        lower = max(candidates, key=lambda r: r.get('chars', 0)) if candidates else None
        answers = []
        if lower:
            try:
                text = _read_cache(lower)
                starts = list(re.finditer(r'(?m)^\s*\d+\s*[\.．、]\s*[【\[]?答案', text))
                for i, m in enumerate(starts):
                    end = starts[i + 1].start() if i + 1 < len(starts) else len(text)
                    block = text[m.end():end]
                    hit = re.search(r'([A-D](?:\s*[、，,/]\s*[A-D])*)', block)
                    if hit:
                        answer = _answer_token(hit.group(1))
                        explanation = block[hit.end():].strip()
                        number = int(re.search(r'\d+', m.group()).group())
                        answers.append((number, answer, explanation))
            except Exception as exc:
                rejects.append(_reject(lower, f'5000_answer_extract:{type(exc).__name__}'))
        parts = (2024, re.sub(r'.*（', '', upper['source_name']).split('）')[0] or '5000',
                 '2024:' + upper['source_name'])
        paper = _paper_record(upper, '行测', '行测5000题', parts)
        records.append(paper)
        items = upper['_question_items']
        alignment = SequenceMatcher(None, [x[0] for x in items], [x[0] for x in answers], autojunk=False)
        paired = {}
        for match in alignment.get_matching_blocks():
            if match.size >= 10:
                for offset in range(match.size):
                    paired[match.a+offset] = answers[match.b+offset]
        question_type = next((name for name,_ in SECTION_PATTERNS
                              if name in upper['source_name']), '常识判断')
        for index, (number, split, block) in enumerate(items):
            if not split or index not in paired:
                rejects.append(_reject(upper, 'unpaired_book_item' if split else 'invalid_options', number, block))
                continue
            stem, options = split
            if FIGURE_DEPENDENCY_RE.search(stem):
                rejects.append(_reject(upper, 'figure_not_extractable', number, stem))
                continue
            _, answer, explanation = paired[index]
            # The answer letter was consumed by the pairing regex, leaving the
            # explanation starting with a stray "。".
            explanation = re.sub(r'^\s*[。.、,，]\s*', '', explanation)
            record = _objective_record(upper, paper, index+1, stem, options, answer,
                                       explanation, question_type, 'xingce_5000')
            record['book_no'] = number
            accuracy = re.search(r'正确率为\s*([\d.]+)%', explanation)
            if accuracy:
                record['source_accuracy_rate'] = float(accuracy.group(1))
            mistake = re.search(r'易错项为\s*([A-D])', explanation)
            if mistake:
                record['common_mistake'] = mistake.group(1)
            records.append(record)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description='Parse cached PDF text into JSONL records')
    parser.add_argument('--include-extras', action='store_true',
                        help='discover and extract the 660/5000 source PDFs')
    args = parser.parse_args(argv)
    index_path = build_path('extract_index.jsonl')
    rows = list(read_jsonl(index_path) or [])
    if args.include_extras:
        rows = _discover_extra_rows(rows)
    records: list[dict] = []
    rejects: list[dict] = []
    _parse_objective_sources(rows, records, rejects)
    _parse_subjective_sources(rows, records, rejects)
    _parse_extra_sources(rows, records, rejects)
    parsed_path = build_path('parsed.jsonl')
    rejects_path = build_path('rejects.jsonl')
    with io.open(parsed_path, 'w', encoding='utf-8') as fh:
        for record in records:
            fh.write(json.dumps(record, ensure_ascii=False) + '\n')
    with io.open(rejects_path, 'w', encoding='utf-8') as fh:
        for reject in rejects:
            fh.write(json.dumps(reject, ensure_ascii=False) + '\n')
    objective_count = sum(r.get('kind') == 'objective' for r in records)
    subjective_count = sum(r.get('kind') == 'subjective' for r in records)
    paper_count = sum(r.get('kind') == 'paper' for r in records)
    progress(f'parse done papers={paper_count} objective={objective_count} subjective={subjective_count} rejects={len(rejects)}')
    progress('parsed -> ' + parsed_path)
    progress('rejects -> ' + rejects_path)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
