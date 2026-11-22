"""Stage 5: derive shenlun scoring rubrics from the real reference answers.

The rubric points are NOT invented — each point is a verbatim span of the
official reference answer already loaded into ``fr_question.answer``.  We only
split that answer on its own enumeration markers ((1)(2), （一）（二）, 1. 2.,
一、二、) and record each span as a scoring point.

Per-point score: the source PDFs give a total ``points`` per question but no
per-point weights, so we split the total evenly across the extracted points and
let the last point absorb the rounding remainder.  This is a documented,
transparent heuristic, not a claim about the official marking scheme.

Answers with no enumeration (e.g. 大作文) get a single holistic point worth the
full mark — point-by-point recall does not apply to an essay, so scoring there
falls back to coverage of the whole reference.

Idempotent: a question's existing rubric rows are deleted and rebuilt each run.
"""
from __future__ import annotations

import argparse
import os
import re

import pymysql

# Enumeration markers, tried in priority order. Each returns the list of spans.
MARKER_PATTERNS = [
    re.compile(r'[(（]\s*([0-9]{1,2})\s*[)）]'),          # (1) （1）
    re.compile(r'[(（]\s*([一二三四五六七八九十]{1,3})\s*[)）]'),  # （一）
    re.compile(r'(?:^|[\n；;])\s*([0-9]{1,2})[.．、]'),    # 1. 1、 at line start
    re.compile(r'(?:^|[\n；;])\s*([一二三四五六七八九十]{1,3})[、.．]'),  # 一、
]

MIN_POINT_LEN = 6  # a span shorter than this is noise, merge into previous


def split_points(answer: str) -> list[str]:
    """Split a reference answer into scoring spans on its own markers.

    Returns the list of point texts. If no marker splits it into >=2 parts,
    returns a single-element list (the whole answer) — a holistic point.
    """
    text = answer.strip()
    for pat in MARKER_PATTERNS:
        matches = list(pat.finditer(text))
        if len(matches) < 2:
            continue
        spans = []
        for i, m in enumerate(matches):
            start = m.start()
            end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
            span = text[start:end].strip()
            if span:
                spans.append(span)
        # merge too-short trailing fragments into the previous point
        merged: list[str] = []
        for span in spans:
            if merged and len(re.sub(r'\s+', '', span)) < MIN_POINT_LEN:
                merged[-1] = merged[-1] + ' ' + span
            else:
                merged.append(span)
        if len(merged) >= 2:
            return merged
    return [text]


def even_scores(total_points: float, n: int) -> list[float]:
    """Split total evenly across n points; last absorbs the remainder.

    Rounded to 1 decimal so the sum equals the total exactly.
    """
    if n <= 0:
        return []
    each = round(total_points / n, 1)
    scores = [each] * (n - 1)
    scores.append(round(total_points - each * (n - 1), 1))
    return scores


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description='Derive shenlun rubrics from reference answers')
    parser.add_argument('--host', default=os.getenv('HONGHU_DB_HOST', '127.0.0.1'))
    parser.add_argument('--port', type=int, default=int(os.getenv('HONGHU_DB_PORT', '3306')))
    parser.add_argument('--user', default=os.getenv('HONGHU_DB_USER', 'root'))
    parser.add_argument('--password', default=os.getenv('HONGHU_DB_PASSWORD'))
    parser.add_argument('--database', default=os.getenv('HONGHU_DB_NAME', 'honghu'))
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args(argv)

    if not args.password:
        parser.error('Set HONGHU_DB_PASSWORD or pass --password')

    conn = pymysql.connect(
        host=args.host, port=args.port, user=args.user, password=args.password,
        database=args.database, charset='utf8mb4', autocommit=False)
    cur = conn.cursor()

    cur.execute("SELECT fr_question_id, points, answer FROM fr_question "
                "WHERE status = 1 AND answer IS NOT NULL AND CHAR_LENGTH(answer) > 20")
    rows = cur.fetchall()

    total_q = 0
    total_points = 0
    holistic = 0
    for fr_id, points, answer in rows:
        spans = split_points(answer)
        if len(spans) == 1:
            holistic += 1
        mark = float(points) if points else float(len(spans) * 5)
        scores = even_scores(mark, len(spans))
        if args.dry_run:
            total_q += 1
            total_points += len(spans)
            continue
        cur.execute("DELETE FROM shenlun_rubric WHERE fr_question_id = %s", (fr_id,))
        for order, (span, score) in enumerate(zip(spans, scores), start=1):
            cur.execute(
                "INSERT INTO shenlun_rubric (fr_question_id, point_order, point_text, point_score) "
                "VALUES (%s,%s,%s,%s)", (fr_id, order, span[:2000], score))
        total_q += 1
        total_points += len(spans)

    if not args.dry_run:
        conn.commit()
    conn.close()

    print(f"questions processed : {total_q}")
    print(f"rubric points        : {total_points}")
    print(f"holistic (1-point)   : {holistic}")
    print(f"avg points/question  : {total_points / total_q:.2f}" if total_q else "no questions")
    print("[dry-run] nothing written" if args.dry_run else "committed")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
