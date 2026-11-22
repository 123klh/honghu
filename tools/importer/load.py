"""Stage 4: idempotently load normalized importer rows into MySQL.

Writes through a real driver (PyMySQL) with parameterized batch statements.
``LOAD DATA LOCAL INFILE`` is deliberately avoided: it needs the server-side
GLOBAL ``local_infile`` switched on, and changing a server's global config to
import a question bank is the wrong trade.

Idempotency rests on business keys, not auto-increment ids:
  * ``paper``      -> ``descr = 'import:<paper_key>'``
  * ``question``   -> ``question_code`` UNIQUE
  * ``fr_question``-> ``question_code`` UNIQUE
Running the loader twice therefore updates the same rows and leaves every
count unchanged.
"""
from __future__ import annotations

import argparse
import os
import re
from collections import Counter

import pymysql

from common import Report, build_path, progress, read_jsonl

ANSWER_RE = re.compile(r'^[A-D]{1,4}$')
PLACEHOLDER_RE = re.compile(r'题目正在全力以赴征集|(?:题目|选项).{0,8}缺失')

BATCH = 500


def validate_objective(row: dict) -> str | None:
    stem = str(row.get('question_stem') or '').strip()
    options = [str(row.get(f'option_{letter}') or '').strip() for letter in 'abcd']
    answer = ''.join(dict.fromkeys(str(row.get('answer') or '').upper()))
    if not stem:
        return 'empty_stem'
    if PLACEHOLDER_RE.search(stem) or any(value == '缺失' for value in options):
        return 'placeholder'
    if any(not value for value in options):
        return 'invalid_options'
    if not ANSWER_RE.fullmatch(answer):
        return 'invalid_answer'
    return None


def validate_subjective(row: dict) -> str | None:
    if not str(row.get('fr_question_stem') or '').strip():
        return 'empty_stem'
    if len(re.sub(r'\s+', '', str(row.get('answer') or ''))) <= 50:
        return 'short_answer'
    return None


def _paper_name(row: dict) -> str:
    name = str(row.get('paper_name') or row.get('paper_key') or '').strip()
    name = re.sub(r'\.pdf$', '', name, flags=re.I)
    return name[:200]


def normalized_rows(path: str):
    """Group rows by business key.

    Duplicate ``question_code`` values are reported, never silently collapsed:
    a collision means parse.py produced two rows for one code and that is a
    parser bug, not something the loader should paper over.
    """
    papers: dict[str, dict] = {}
    objectives: dict[str, dict] = {}
    subjectives: dict[str, dict] = {}
    rejected = Counter()

    for row in read_jsonl(path) or []:
        kind = row.get('kind')
        if kind == 'paper' and row.get('paper_key'):
            papers[row['paper_key']] = row
            continue
        if kind == 'objective':
            reason = validate_objective(row)
            bucket = objectives
        elif kind == 'subjective':
            reason = validate_subjective(row)
            bucket = subjectives
        else:
            continue
        code = row.get('question_code')
        if reason:
            rejected[f'{kind}:{reason}'] += 1
            continue
        if not code:
            rejected[f'{kind}:missing_code'] += 1
            continue
        if code in bucket:
            rejected[f'{kind}:duplicate_code'] += 1
            continue
        if kind == 'objective':
            row['answer'] = ''.join(dict.fromkeys(row['answer'].upper()))
        bucket[code] = row

    for row in list(objectives.values()) + list(subjectives.values()):
        key = row.get('paper_key')
        if key and key not in papers:
            papers[key] = {
                'paper_key': key,
                'paper_name': row.get('paper_name') or key,
                'source': row.get('source_kind') or 'PDF题库导入',
                'debut': '',
                'subject': row.get('subject'),
            }
    return list(papers.values()), list(objectives.values()), list(subjectives.values()), rejected


COUNT_TABLES = ('paper', 'question', 'fr_question',
                'question_knowledge', 'question_difficulty')


def database_counts(cur) -> dict[str, int]:
    counts = {}
    for table in COUNT_TABLES:
        cur.execute('SELECT COUNT(*) FROM `%s`' % table)
        counts[table] = cur.fetchone()[0]
    for table in ('question', 'fr_question'):
        cur.execute('SELECT COUNT(*) FROM `%s` WHERE status = 1' % table)
        counts[table + '(active)'] = cur.fetchone()[0]
    return counts


def _chunks(rows: list, size: int = BATCH):
    for start in range(0, len(rows), size):
        yield rows[start:start + size]


def upsert_papers(cur, papers: list[dict]) -> dict[str, int]:
    """Insert missing papers, refresh existing ones, return paper_key -> id."""
    if not papers:
        return {}
    keys = ['import:' + row['paper_key'] for row in papers]
    existing: dict[str, int] = {}
    for chunk in _chunks(keys):
        placeholders = ','.join(['%s'] * len(chunk))
        cur.execute(
            'SELECT paper_id, descr FROM paper WHERE descr IN (%s)' % placeholders, chunk)
        for paper_id, descr in cur.fetchall():
            existing[descr] = paper_id

    to_insert = [row for row in papers if 'import:' + row['paper_key'] not in existing]
    if to_insert:
        values = [(
            _paper_name(row),
            (row.get('source') or 'PDF题库导入')[:100],
            (row.get('debut') or '')[:50],
            120 if row.get('subject') == '行测' else 180,
            (row.get('subject') or '')[:50],
            'import:' + row['paper_key'],
        ) for row in to_insert]
        for chunk in _chunks(values):
            cur.executemany(
                'INSERT INTO paper (paper_name, source, debut, exam_duration,'
                ' status, subject, descr) VALUES (%s,%s,%s,%s,1,%s,%s)', chunk)

    updates = [(
        _paper_name(row),
        (row.get('source') or 'PDF题库导入')[:100],
        (row.get('debut') or '')[:50],
        (row.get('subject') or '')[:50],
        'import:' + row['paper_key'],
    ) for row in papers if 'import:' + row['paper_key'] in existing]
    for chunk in _chunks(updates):
        cur.executemany(
            'UPDATE paper SET paper_name=%s, source=%s, debut=%s, subject=%s,'
            ' status=1 WHERE descr=%s', chunk)

    # Re-read so both freshly inserted and pre-existing papers are covered.
    mapping: dict[str, int] = {}
    for chunk in _chunks(keys):
        placeholders = ','.join(['%s'] * len(chunk))
        cur.execute(
            'SELECT paper_id, descr FROM paper WHERE descr IN (%s)' % placeholders, chunk)
        for paper_id, descr in cur.fetchall():
            mapping[descr[len('import:'):]] = paper_id
    return mapping


QUESTION_SQL = """
INSERT INTO question
 (question_code, subject, question_stem, option_a, option_b, option_c, option_d,
  enclosure, answer, question_type, paper_id, status, explanation)
VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,1,%s)
ON DUPLICATE KEY UPDATE
 subject=VALUES(subject), question_stem=VALUES(question_stem),
 option_a=VALUES(option_a), option_b=VALUES(option_b),
 option_c=VALUES(option_c), option_d=VALUES(option_d),
 enclosure=VALUES(enclosure), answer=VALUES(answer),
 question_type=VALUES(question_type), paper_id=VALUES(paper_id),
 status=1, explanation=VALUES(explanation)
"""


def upsert_questions(cur, rows: list[dict], paper_ids: dict[str, int]) -> int:
    total = 0
    for chunk in _chunks(rows):
        values = []
        for row in chunk:
            material = (row.get('material') or '').strip()
            stem = row['question_stem']
            if material:
                # 资料分析 items are unanswerable without their shared material,
                # so it ships as part of the stem rather than being dropped.
                stem = '【材料】\n' + material + '\n\n' + stem
            values.append((
                row['question_code'],
                (row.get('subject') or '')[:50],
                stem,
                row.get('option_a'), row.get('option_b'),
                row.get('option_c'), row.get('option_d'),
                1 if material else 0,
                row['answer'][:10],
                (row.get('question_type') or '')[:50],
                paper_ids.get(row.get('paper_key')),
                (row.get('explanation') or '').strip() or None,
            ))
        cur.executemany(QUESTION_SQL, values)
        total += len(values)
    return total


FR_SQL = """
INSERT INTO fr_question
 (question_code, material, fr_question_stem, answer, fr_question_type, points,
  paper_id, status, subject, descr)
VALUES (%s,%s,%s,%s,%s,%s,%s,1,%s,%s)
ON DUPLICATE KEY UPDATE
 material=VALUES(material), fr_question_stem=VALUES(fr_question_stem),
 answer=VALUES(answer), fr_question_type=VALUES(fr_question_type),
 points=VALUES(points), paper_id=VALUES(paper_id), status=1,
 subject=VALUES(subject)
"""


def upsert_fr_questions(cur, rows: list[dict], paper_ids: dict[str, int]) -> int:
    total = 0
    for chunk in _chunks(rows):
        values = [(
            row['question_code'],
            (row.get('material') or '').strip() or None,
            row['fr_question_stem'],
            row.get('answer'),
            (row.get('fr_question_type') or '')[:50],
            row.get('points'),
            paper_ids.get(row.get('paper_key')),
            (row.get('subject') or '')[:50],
            'PDF题库导入',
        ) for row in chunk]
        cur.executemany(FR_SQL, values)
        total += len(values)
    return total


def deactivate_orphans(cur, table: str, code_column: str, codes: set[str]) -> int:
    """Retire importer-owned rows whose question_code is no longer produced.

    Scoped to rows attached to an imported paper (``descr LIKE 'import:%'``) so
    anything an editor added by hand is left alone.  Rows are set to
    ``status=0`` rather than deleted, because answer_record and wrong_book may
    already reference them.
    """
    cur.execute(
        'SELECT t.%s FROM `%s` t JOIN paper p ON p.paper_id = t.paper_id '
        "WHERE p.descr LIKE 'import:%%' AND t.status = 1" % (code_column, table))
    stale = [row[0] for row in cur.fetchall() if row[0] not in codes]
    if not stale:
        return 0
    for chunk in _chunks(stale):
        placeholders = ','.join(['%s'] * len(chunk))
        cur.execute(
            'UPDATE `%s` SET status = 0 WHERE %s IN (%s)'
            % (table, code_column, placeholders), chunk)
    return len(stale)


def link_knowledge(cur) -> int:
    """Attach each question to the top-level knowledge point of its type."""
    cur.execute("""
INSERT IGNORE INTO question_knowledge (question_id, kp_id)
SELECT q.question_id, kp.kp_id
FROM question q
JOIN knowledge_point kp
  ON kp.kp_name = q.question_type AND kp.parent_id = 0
""")
    return cur.rowcount


def upsert_difficulty(cur, rows: list[dict]) -> int:
    """Store source-provided accuracy stats where the PDFs gave them."""
    payload = []
    for row in rows:
        rate = row.get('source_accuracy_rate')
        mistake = row.get('common_mistake')
        if rate is None and not mistake:
            continue
        difficulty = None if rate is None else round(1.0 - float(rate), 4)
        payload.append((row['question_code'], difficulty, rate,
                        (mistake or '')[:10] or None))
    if not payload:
        return 0
    total = 0
    for chunk in _chunks(payload):
        cur.executemany("""
INSERT INTO question_difficulty
 (question_id, difficulty_value, source_accuracy_rate, common_mistake)
SELECT q.question_id, %s, %s, %s FROM question q WHERE q.question_code = %s
ON DUPLICATE KEY UPDATE
 difficulty_value=VALUES(difficulty_value),
 source_accuracy_rate=VALUES(source_accuracy_rate),
 common_mistake=VALUES(common_mistake)
""", [(d, r, m, code) for code, d, r, m in chunk])
        total += len(chunk)
    return total


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description='Load normalized question data into MySQL')
    parser.add_argument('--input', default=build_path('normalized.jsonl'))
    parser.add_argument('--host', default=os.getenv('HONGHU_DB_HOST', '127.0.0.1'))
    parser.add_argument('--port', type=int, default=int(os.getenv('HONGHU_DB_PORT', '3306')))
    parser.add_argument('--user', default=os.getenv('HONGHU_DB_USER', 'root'))
    parser.add_argument('--password', default=os.getenv('HONGHU_DB_PASSWORD'))
    parser.add_argument('--database', default=os.getenv('HONGHU_DB_NAME', 'honghu'))
    args = parser.parse_args(argv)

    if not args.password:
        parser.error('Set HONGHU_DB_PASSWORD or pass --password')

    if not os.path.isfile(args.input):
        raise SystemExit('normalized input missing; run normalize.py first')

    papers, questions, subjectives, rejected = normalized_rows(args.input)

    report = Report('load_report.txt')
    report.section('Stage 4: MySQL load')
    report.line('input:', args.input)
    report.line('valid papers:', len(papers))
    report.line('valid objective:', len(questions))
    report.line('valid subjective:', len(subjectives))
    for reason, count in sorted(rejected.items()):
        report.line('rejected', reason, count)

    conn = pymysql.connect(
        host=args.host, port=args.port, user=args.user, password=args.password,
        database=args.database, charset='utf8mb4', autocommit=False,
    )
    try:
        with conn.cursor() as cur:
            before = database_counts(cur)
            paper_ids = upsert_papers(cur, papers)
            n_obj = upsert_questions(cur, questions, paper_ids)
            n_fr = upsert_fr_questions(cur, subjectives, paper_ids)
            n_kp = link_knowledge(cur)
            n_diff = upsert_difficulty(cur, questions)
            stale_obj = deactivate_orphans(
                cur, 'question', 'question_code',
                {row['question_code'] for row in questions})
            stale_fr = deactivate_orphans(
                cur, 'fr_question', 'question_code',
                {row['question_code'] for row in subjectives})
            conn.commit()
            after = database_counts(cur)
        report.line('retired stale objective:', stale_obj)
        report.line('retired stale subjective:', stale_fr)
        report.line('papers touched:', len(paper_ids))
        report.line('objective statements:', n_obj)
        report.line('fr statements:', n_fr)
        report.line('knowledge links inserted:', n_kp)
        report.line('difficulty rows:', n_diff)
        report.line('before:', before)
        report.line('after:', after)
    except Exception as exc:
        conn.rollback()
        report.line('ERROR:', type(exc).__name__, str(exc))
        report.close()
        raise
    finally:
        conn.close()
    report.close()
    progress('load done objective=%d subjective=%d' % (len(questions), len(subjectives)))
    progress('counts before=%s' % before)
    progress('counts after =%s' % after)
    progress('report -> ' + build_path('load_report.txt'))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
