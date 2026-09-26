"""Throwaway probe: why do 4 question_code values duplicate, and how is
shared material for 资料分析 laid out?  Reads cache only."""
import io
import json
import os
import re

from common import build_path, read_jsonl, BUILD_DIR
import parse as P

rows = list(read_jsonl(build_path('extract_index.jsonl')))
out = io.open(build_path('probe_dup.txt'), 'w', encoding='utf-8')

targets = {'2013年国家公务员考试《行测》真题卷.pdf',
           '2009年国家公务员考试《行测》真题卷.pdf'}
for row in rows:
    if row.get('source_name') not in targets or not row.get('chars'):
        continue
    text = P._read_cache(row)
    out.write('==== %s chars=%d\n' % (row['source_name'], len(text)))
    nums = [n for n, _ in P._question_blocks(text)]
    out.write('block numbers: %s\n' % nums[:60])
    # locate every block whose number repeats
    seen = {}
    for n, block in P._question_blocks(text):
        seen.setdefault(n, []).append(block)
    for n, blocks in sorted(seen.items()):
        if len(blocks) > 1:
            out.write('\n-- number %d appears %d times\n' % (n, len(blocks)))
            for b in blocks:
                out.write('   [%s]\n' % re.sub(r'\s+', ' ', b)[:260])
    # what precedes the 资料分析 section
    pos = text.find('资料分析')
    if pos > 0:
        out.write('\n-- 资料分析 context --\n')
        out.write(re.sub(r'[ \t]+', ' ', text[pos:pos + 2200]) + '\n')
out.close()
print('-> ' + build_path('probe_dup.txt'))
