"""Dump the requirement section and every answer marker for a few shenlun papers."""
import io
import re
from collections import defaultdict

from common import build_path, read_jsonl
from parse import _paper_parts, _read_cache, _find_requirement_start

WANT = {(2013, '副省'), (2020, '副省'), (2016, '副省'), (2010, '地市'), (2007, '单卷')}

rows = list(read_jsonl(build_path('extract_index.jsonl')))
grouped = defaultdict(list)
for row in rows:
    if row.get('group') not in {'gk_shenlun', 'gk_shenlun_alt'} or not row.get('chars'):
        continue
    parts = _paper_parts(row.get('source_name', ''))
    if parts:
        grouped[(parts[0], parts[1])].append(row)

out = io.open(build_path('probe_sl2.txt'), 'w', encoding='utf-8')
for key in sorted(grouped, key=str):
    if key not in WANT:
        continue
    row = max(grouped[key], key=lambda r: r.get('chars', 0))
    text = _read_cache(row)
    req_start = _find_requirement_start(text)
    out.write('\n' + '#' * 78 + '\n%s %s req_start=%s\n' % (key, row['source_name'], req_start))
    first = text.find('参考答案')
    out.write('--- REQUIREMENT SECTION (req_start..first 参考答案) ---\n')
    seg = text[req_start if req_start is not None else 0:first if first >= 0 else len(text)]
    out.write(seg[:2600] + '\n')
    out.write('--- ANSWER MARKER CONTEXT ---\n')
    for m in re.finditer(r'参考答案', text):
        out.write('  @%d | %s\n' % (
            m.start(), re.sub(r'\s+', ' ', text[max(0, m.start() - 80):m.start() + 90])))
out.close()
print('-> ' + build_path('probe_sl2.txt'))
