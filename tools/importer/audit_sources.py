"""Inspect cached source structures without re-extracting PDFs."""
import collections
import json
import re
from common import Report, read_jsonl, build_path
from parse import _read_cache

report = Report('source_audit.txt')
for row in read_jsonl(build_path('extract_index.jsonl')):
    if row['group'] == 'xingce_5000':
        text = _read_cache(row)
        starts = list(re.finditer(r'(?m)^\s*(\d{1,3})\s*\.\s*(?:\(\s*(?:19|20)\d{2}|【答案】)', text))
        report.line(row['source_name'], len(starts), [m.group(1) for m in starts[:30]])
        for m in starts[:2]:
            report.line(text[m.start():m.start()+1600])
    elif row['group'].startswith('gk_shenlun'):
        text = _read_cache(row)
        report.line(row['source_name'])
        for m in re.finditer(r'(?m)^.*(?:参考答案|申论要求|作答要求|【问题|第.题).*$', text):
            report.line(text[max(0,m.start()-30):m.end()+180])
report.close()
rows = list(read_jsonl(build_path('parsed.jsonl')))
report = Report('sample_before_normalize.txt')
report.line(collections.Counter(r.get('source_kind') for r in rows if r['kind']=='objective'))
report.line(collections.Counter(r.get('question_type') for r in rows if r['kind']=='objective'))
for kind in ('gk','xingce_5000','changshi_660'):
    items=[r for r in rows if r.get('source_kind')==kind]
    for r in items[:3]:
        report.line(json.dumps(r,ensure_ascii=False))
report.close()
print('audit written')
