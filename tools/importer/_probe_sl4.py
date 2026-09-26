"""Refined shenlun alignment: answer markers are the authority for count+numbering."""
import io
import re
from collections import defaultdict

from common import build_path, read_jsonl
from parse import _paper_parts, _read_cache, _find_requirement_start
from _probe_sl3 import (ANSWER_MARKER_RE, ANSWER_MARKER_LOOSE_RE, REQ_MARKER_RE,
                        _num, answer_section_start)

# Real questions always state their point value; nested "要求" sub-items never do.
SCORE_RE = re.compile(r'[（(]\s*(\d{1,3})\s*分\s*[）)]')
DETAIL = {(2000, '单卷'), (2006, '单卷'), (2021, '副省'), (2022, '地市'), (2023, '副省')}


def _segments(text, matches):
    out = []
    for i, m in enumerate(matches):
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        out.append((_num(m), m.start(), text[m.end():end]))
    return out


def requirement_candidates(req_text):
    matches = list(REQ_MARKER_RE.finditer(req_text))
    segs = _segments(req_text, matches)
    out = []
    for i, (num, start, body) in enumerate(segs):
        if not SCORE_RE.search(body):
            continue
        out.append({'num': num, 'idx': i, 'start': start,
                    'points': int(SCORE_RE.search(body).group(1))})
    # Each stem runs from its marker to the next *kept* marker.
    for pos, item in enumerate(out):
        end = out[pos + 1]['start'] if pos + 1 < len(out) else len(req_text)
        item['stem'] = req_text[item['start']:end].strip()
    return out


def answer_blocks(ans_text):
    matches = list(ANSWER_MARKER_RE.finditer(ans_text))
    if len(matches) < 2:
        matches = list(ANSWER_MARKER_LOOSE_RE.finditer(ans_text))
    segs = _segments(ans_text, matches)
    return [{'num': num, 'text': body.strip()} for num, _, body in segs
            if len(re.sub(r'\s+', '', body)) >= 30]


def align(reqs, answers):
    """Greedy left-to-right match of answer number tokens onto requirements."""
    if not reqs or not answers:
        return []
    if all(not a['num'] for a in answers):
        # Unnumbered answers (2007-2008, 2022-2023): pair positionally.
        if len(reqs) == len(answers):
            return list(zip(reqs, answers))
        return []
    pairs = []
    cursor = 0
    for ans in answers:
        while cursor < len(reqs) and reqs[cursor]['num'] != ans['num']:
            cursor += 1
        if cursor >= len(reqs):
            break
        pairs.append((reqs[cursor], ans))
        cursor += 1
    return pairs


rows = list(read_jsonl(build_path('extract_index.jsonl')))
grouped = defaultdict(list)
for row in rows:
    if row.get('group') not in {'gk_shenlun', 'gk_shenlun_alt'} or not row.get('chars'):
        continue
    parts = _paper_parts(row.get('source_name', ''))
    if parts:
        grouped[(parts[0], parts[1])].append(row)

out = io.open(build_path('probe_sl4.txt'), 'w', encoding='utf-8')
total = 0
for key in sorted(grouped, key=str):
    row = max(grouped[key], key=lambda r: r.get('chars', 0))
    text = _read_cache(row)
    req_start = _find_requirement_start(text)
    ans_start = answer_section_start(text, req_start)
    if ans_start < 0:
        out.write('%-18s SKIP no_answer_section\n' % (key,))
        continue
    req_text = text[req_start:ans_start] if req_start is not None else ''
    if req_text:
        req_text = req_text.split('\n', 1)[-1]
    reqs = requirement_candidates(req_text)
    answers = answer_blocks(text[ans_start:])
    pairs = align(reqs, answers)
    total += len(pairs)
    out.write('%-18s reqs=%d answers=%d PAIRED=%d  req=%s ans=%s\n' % (
        key, len(reqs), len(answers), len(pairs),
        [r['num'] for r in reqs], [a['num'] for a in answers]))
    if key in DETAIL or len(pairs) < min(len(reqs), len(answers)):
        for r in reqs:
            out.write('    REQ %s (%d分) | %s\n' % (r['num'], r['points'],
                                                    re.sub(r'\s+', ' ', r['stem'])[:130]))
        for a in answers:
            out.write('    ANS %s | %s\n' % (a['num'], re.sub(r'\s+', ' ', a['text'])[:130]))
out.write('\nTOTAL paired=%d\n' % total)
out.close()
print('-> ' + build_path('probe_sl4.txt'))
