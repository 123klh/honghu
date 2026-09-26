"""Quality audit over build/parsed.jsonl.

Writes build/parsed_audit.txt.  Read-only: never re-extracts PDFs.
Purpose is to turn "7921 candidates" into evidence about whether the stems,
options and answers are actually usable.
"""
from __future__ import annotations

import collections
import io
import random
import re

from common import build_path, read_jsonl, progress

NOISE_PATTERNS = [
    ('gongzhonghao', re.compile(r'公众号')),
    ('fenbi_app', re.compile(r'粉笔APP|打开粉笔')),
    ('indd', re.compile(r'\.indd', re.I)),
    ('page_marker', re.compile(r'第\s*\d+\s*页')),
    ('url', re.compile(r'https?://|www\.')),
    ('answer_leak', re.compile(r'正确答案|【解析】')),
    ('option_leak', re.compile(r'(?<![A-Za-z])[BCD]\s*[\.．、]\s*\S')),
    ('kangxi', re.compile(r'[⺀-⿟]')),
    ('dup_han', re.compile(r'([一-鿿])\1{2,}')),
]

IMAGE_HINT = re.compile(r'如[下图右左]|图形|下列图|所示图|如图|图中|下图|统计图|柱状|折线|饼图')
MATERIAL_HINT = re.compile(r'根据(?:以下|下列|上述)?(?:资料|材料|统计表|图表)|所给资料|下列资料')


def main() -> int:
    rows = list(read_jsonl(build_path('parsed.jsonl')))
    objective = [r for r in rows if r.get('kind') == 'objective']
    subjective = [r for r in rows if r.get('kind') == 'subjective']
    papers = [r for r in rows if r.get('kind') == 'paper']
    rep = io.open(build_path('parsed_audit.txt'), 'w', encoding='utf-8')

    def out(*parts):
        rep.write(' '.join(str(p) for p in parts) + '\n')

    out('rows', len(rows), 'objective', len(objective),
        'subjective', len(subjective), 'paper', len(papers))

    by_kind = collections.Counter(r.get('source_kind') for r in objective)
    out('\n-- objective by source_kind --')
    for k, v in by_kind.most_common():
        out(f'  {k}: {v}')

    by_type = collections.Counter(r.get('question_type') for r in objective)
    out('\n-- objective by question_type --')
    for k, v in by_type.most_common():
        out(f'  {k}: {v}')

    codes = collections.Counter(r['question_code'] for r in objective)
    dups = {c: n for c, n in codes.items() if n > 1}
    out('\n-- duplicate question_code --', len(dups))
    for code, n in list(dups.items())[:20]:
        out(f'  {code} x{n}')
        for r in objective:
            if r['question_code'] == code:
                out('     no=%s src=%s stem=%s' % (
                    r.get('question_no'), r.get('source_name'),
                    re.sub(r'\s+', ' ', r.get('question_stem', ''))[:70]))

    out('\n-- noise hits in stem/options --')
    noise = collections.Counter()
    noise_examples: dict[str, list] = collections.defaultdict(list)
    for r in objective:
        blob = '\n'.join([r.get('question_stem', ''), r.get('option_a', ''),
                          r.get('option_b', ''), r.get('option_c', ''), r.get('option_d', '')])
        for name, pat in NOISE_PATTERNS:
            if pat.search(blob):
                noise[name] += 1
                if len(noise_examples[name]) < 3:
                    noise_examples[name].append(r)
    for name, n in noise.most_common():
        out(f'  {name}: {n}')
        for r in noise_examples[name]:
            out('     %s | %s' % (r.get('question_code'),
                                  re.sub(r'\s+', ' ', r.get('question_stem', ''))[:110]))

    out('\n-- length distribution --')
    for field in ('question_stem', 'option_a', 'option_b', 'option_c', 'option_d'):
        lens = sorted(len(r.get(field) or '') for r in objective)
        if not lens:
            continue
        out('  %-14s min=%d p05=%d p50=%d p95=%d max=%d' % (
            field, lens[0], lens[len(lens) // 20], lens[len(lens) // 2],
            lens[len(lens) * 19 // 20], lens[-1]))

    short_stem = [r for r in objective if len(re.sub(r'\s+', '', r.get('question_stem') or '')) < 10]
    out('\n-- stems shorter than 10 chars --', len(short_stem))
    for r in short_stem[:15]:
        out('  %s | %s | %r' % (r.get('question_code'), r.get('source_name'),
                                r.get('question_stem')))

    needs_image = [r for r in objective if IMAGE_HINT.search(r.get('question_stem') or '')]
    needs_material = [r for r in objective if MATERIAL_HINT.search(r.get('question_stem') or '')]
    out('\n-- rows referencing a figure --', len(needs_image))
    out('-- rows referencing shared material --', len(needs_material))
    out('   (both categories are unusable unless the asset is captured)')

    out('\n-- answers --')
    ans = collections.Counter(r.get('answer') for r in objective)
    for k, v in sorted(ans.items(), key=lambda kv: -kv[1])[:15]:
        out(f'  {k}: {v}')
    no_expl = [r for r in objective if len(re.sub(r'\s+', '', r.get('explanation') or '')) < 10]
    out('  rows with explanation < 10 chars:', len(no_expl))

    out('\n-- subjective --')
    for r in subjective:
        out('  %s no=%s points=%s ans_len=%d type=%s mat_len=%d' % (
            r.get('source_name', '')[:40], r.get('question_no'), r.get('points'),
            len(re.sub(r'\s+', '', r.get('answer') or '')), r.get('fr_question_type'),
            len(re.sub(r'\s+', '', r.get('material') or ''))))

    rnd = random.Random(20260921)
    out('\n-- fixed-seed sample of 20 objective rows --')
    pool = list(objective)
    rnd.shuffle(pool)
    picks = []
    seen_kinds = set()
    for r in pool:
        if r.get('source_kind') not in seen_kinds:
            picks.append(r)
            seen_kinds.add(r.get('source_kind'))
    picks.extend(pool[:20 - len(picks)])
    for r in picks[:20]:
        out('')
        out('  code=%s kind=%s no=%s type=%s answer=%s' % (
            r.get('question_code'), r.get('source_kind'), r.get('question_no'),
            r.get('question_type'), r.get('answer')))
        out('  src=%s' % r.get('source_name'))
        out('  stem=%s' % re.sub(r'\s+', ' ', r.get('question_stem') or '')[:400])
        for label in 'abcd':
            out('    %s) %s' % (label.upper(),
                                re.sub(r'\s+', ' ', r.get('option_' + label) or '')[:160]))
        out('  expl=%s' % re.sub(r'\s+', ' ', r.get('explanation') or '')[:300])

    rejects = list(read_jsonl(build_path('rejects.jsonl')))
    out('\n-- rejects by reason --', len(rejects))
    for k, v in collections.Counter(r.get('reason') for r in rejects).most_common():
        out(f'  {k}: {v}')
    rep.close()
    progress('audit -> ' + build_path('parsed_audit.txt'))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
