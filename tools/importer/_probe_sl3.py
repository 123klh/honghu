"""Trial run of hierarchical shenlun alignment; measures yield before parse.py changes."""
import io
import re
from collections import defaultdict

from common import build_path, read_jsonl
from parse import _paper_parts, _read_cache, _find_requirement_start

_NUM = r'\d{1,2}|[一二三四五六七八九十]{1,3}'
# Numbering prefixes seen across 2000-2024: "一、" "1." "1、" "(一)" "（1）" "第一题".
# Parenthesised forms carry no trailing punctuation, bare forms always do.
_PREFIX = (
    r'(?:'
    rf'[（(][ \t]*(?P<np>{_NUM})[ \t]*[）)][ \t]*[、.．]?'
    rf'|第[ \t]*(?P<nt>{_NUM})[ \t]*题[ \t]*[、.．：:]?'
    rf'|(?P<nb>{_NUM})[ \t]*[、.．]'
    r')[ \t]*'
)
_ANSWER_WORD = r'【?(?:参考答案|答案提示|答案要点|参考例文|参考范文)】?[ \t]*[:：]?'

# 2007-2008 omit the number entirely, hence the optional prefix.
ANSWER_MARKER_RE = re.compile(rf'(?m)^[ \t]*(?:{_PREFIX})?{_ANSWER_WORD}[ \t]*$')
ANSWER_MARKER_LOOSE_RE = re.compile(rf'(?m)^[ \t]*(?:{_PREFIX})?{_ANSWER_WORD}')
REQ_MARKER_RE = re.compile(rf'(?m)^[ \t]*{_PREFIX}')


def _num(match) -> str:
    return match.group('np') or match.group('nt') or match.group('nb') or ''
# A real question always states how the answer is constrained.
REAL_QUESTION_RE = re.compile(r'要求|不超过\s*\d+\s*字|不少于\s*\d+\s*字|\d+\s*[~～-]\s*\d+\s*字')


def _segments(text: str, matches: list) -> list[tuple[str, int, str]]:
    """(number_token, start, body) for each match, body running to next match."""
    out = []
    for i, m in enumerate(matches):
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        out.append((_num(m), m.start(), text[m.end():end]))
    return out


def requirement_items(req_text: str) -> list[tuple[str, str]]:
    matches = list(REQ_MARKER_RE.finditer(req_text))
    if not matches:
        return []
    segs = _segments(req_text, matches)
    keep = [i for i, (_, _, body) in enumerate(segs) if REAL_QUESTION_RE.search(body)]
    if not keep:
        keep = list(range(len(segs)))
    items = []
    for pos, i in enumerate(keep):
        start = matches[i].start()
        end = matches[keep[pos + 1]].start() if pos + 1 < len(keep) else len(req_text)
        items.append((segs[i][0], req_text[start:end].strip()))
    return items


def answer_items(ans_text: str) -> list[tuple[str, str]]:
    matches = list(ANSWER_MARKER_RE.finditer(ans_text))
    if len(matches) < 2:
        matches = list(ANSWER_MARKER_LOOSE_RE.finditer(ans_text))
    if not matches:
        return []
    segs = _segments(ans_text, matches)
    # A marker whose body is empty is a group header for nested answers below it.
    return [(num, body.strip()) for num, _, body in segs
            if len(re.sub(r'\s+', '', body)) >= 10]


def answer_section_start(text: str, req_start) -> int:
    """Where the solutions begin: the '(解析)' banner if present, else 1st marker."""
    search_from = req_start or 0
    banner = None
    for m in re.finditer(r'(?m)^.*[（(]\s*解析\s*[）)].*$', text):
        if m.start() >= search_from:
            banner = m.end()
            break
    if banner is not None:
        return banner
    m = ANSWER_MARKER_LOOSE_RE.search(text, search_from)
    return m.start() if m else -1


rows = list(read_jsonl(build_path('extract_index.jsonl')))
grouped = defaultdict(list)
for row in rows:
    if row.get('group') not in {'gk_shenlun', 'gk_shenlun_alt'} or not row.get('chars'):
        continue
    parts = _paper_parts(row.get('source_name', ''))
    if parts:
        grouped[(parts[0], parts[1])].append(row)

out = io.open(build_path('probe_sl3.txt'), 'w', encoding='utf-8')
total_paired = 0
total_papers = 0
for key in sorted(grouped, key=str):
    row = max(grouped[key], key=lambda r: r.get('chars', 0))
    text = _read_cache(row)
    req_start = _find_requirement_start(text)
    ans_start = answer_section_start(text, req_start)
    if ans_start < 0:
        out.write('%s SKIP no_answer_section\n' % (key,))
        continue
    req_text = text[req_start:ans_start] if req_start is not None else ''
    if req_text:
        req_text = req_text.split('\n', 1)[-1]
    reqs = requirement_items(req_text)
    answers = answer_items(text[ans_start:])
    paired = len(reqs) if len(reqs) == len(answers) else 0
    total_papers += 1
    total_paired += paired
    out.write('%-18s reqs=%d answers=%d paired=%s  req_nums=%s ans_nums=%s\n' % (
        key, len(reqs), len(answers), 'YES' if paired else 'NO',
        [n for n, _ in reqs], [n for n, _ in answers]))
    if not paired:
        for n, stem in reqs:
            out.write('    REQ %s | %s\n' % (n, re.sub(r'\s+', ' ', stem)[:150]))
        for n, ans in answers:
            out.write('    ANS %s | %s\n' % (n, re.sub(r'\s+', ' ', ans)[:150]))
out.write('\nTOTAL papers=%d paired_questions=%d\n' % (total_papers, total_paired))
out.close()
print('-> ' + build_path('probe_sl3.txt'))
