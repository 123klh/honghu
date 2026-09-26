"""Probe shenlun answer-section shapes so alignment can be fixed with evidence."""
import io
import json
import re
from collections import defaultdict

from common import build_path, read_jsonl
from parse import (_paper_parts, _read_cache, _find_requirement_start,
                   REQUIREMENT_START_RE, _ordinal, _answer_chunks)

rows = list(read_jsonl(build_path('extract_index.jsonl')))
grouped = defaultdict(list)
for row in rows:
    if row.get('group') not in {'gk_shenlun', 'gk_shenlun_alt'} or not row.get('chars'):
        continue
    parts = _paper_parts(row.get('source_name', ''))
    if parts:
        grouped[(parts[0], parts[1])].append(row)

out = io.open(build_path('probe_sl.txt'), 'w', encoding='utf-8')
for key in sorted(grouped, key=str):
    row = max(grouped[key], key=lambda r: r.get('chars', 0))
    text = _read_cache(row)
    req_start = _find_requirement_start(text)
    out.write('\n' + '=' * 78 + '\n')
    out.write('%s  %s  chars=%d  req_start=%s\n' % (key, row['source_name'], len(text), req_start))
    # every answer-ish marker present
    for pat in (r'【参考答案】', r'参考答案', r'【答案】', r'答案要点', r'参考例文',
                r'【解析】', r'【思路点拨】', r'第一题', r'问题一'):
        n = len(re.findall(pat, text))
        if n:
            out.write('  marker %-10s x%d\n' % (pat, n))
    if req_start is not None:
        ref = text.find('参考答案', req_start)
        req_text = text[req_start:(ref if ref >= 0 else len(text))].split('\n', 1)[-1]
        nums = [_ordinal(m.group('n')) for m in REQUIREMENT_START_RE.finditer(req_text)]
        out.write('  requirement numbers: %s\n' % [n for n in nums if n and n <= 20])
    out.write('  answer chunks: %d  lens=%s\n' % (
        len(_answer_chunks(text)),
        [len(re.sub(r'\s+', '', c)) for c in _answer_chunks(text)]))
    tail_pos = text.rfind('参考答案')
    if tail_pos >= 0:
        out.write('  --- 900 chars after last 参考答案 ---\n')
        out.write(re.sub(r'\n{2,}', '\n', text[tail_pos:tail_pos + 900]) + '\n')
out.close()
print('-> ' + build_path('probe_sl.txt'))
