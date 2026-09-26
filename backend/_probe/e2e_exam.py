"""End-to-end check of the V4 exam fixes against the running backend.

Verifies, through the real HTTP API and the real database:
  1. exam_question snapshot is written at start
  2. exam_score.score stays NULL at start and paper.avg_score is untouched
  3. a question outside the snapshot is rejected
  4. the score denominator is the whole paper, not the submitted count
  5. duplicate submit is rejected
  6. answer_record.score_rate is NULL for objective answers
"""
import json
import os
import urllib.error
import urllib.request

import pymysql

BASE = 'http://localhost:8080'


def db():
    return pymysql.connect(
        host='127.0.0.1', user='root',
        password=os.getenv('HONGHU_DB_PASSWORD', '1234'),
        database='honghu', charset='utf8mb4',
        # 每条读都要看到 HTTP 调用刚写入的数据；REPEATABLE READ 下
        # 长事务会一直读到连接打开时的快照。
        autocommit=True)


def call(path, token=None, body=None, method='POST'):
    data = None if body is None else json.dumps(body).encode()
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        return 200, json.loads(urllib.request.urlopen(req).read().decode())
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode()


def one(cur, sql, args=()):
    cur.execute(sql, args)
    return cur.fetchone()


def rejected(code, body):
    """True when the request was refused.

    GlobalExceptionHandler answers BusinessException with HTTP 200 and an
    envelope whose ``code`` carries the real status, so the HTTP status alone
    does not tell you whether the call was accepted.
    """
    if code != 200:
        return True
    return isinstance(body, dict) and body.get('code') != 200


checks = []


def check(name, ok, detail=''):
    checks.append((name, ok, detail))
    print(('PASS  ' if ok else 'FAIL  ') + name + (('  ' + str(detail)) if detail else ''))


def main():
    user = 'e2e_%d' % os.getpid()
    code, _ = call('/api/auth/user/register', body={
        'userName': user, 'password': 'Test@12345', 'email': user + '@example.com'})
    check('register', code == 200, code)
    code, payload = call('/api/auth/user/login', body={
        'userName': user, 'password': 'Test@12345'})
    token = payload['data']['token']

    conn = db()
    cur = conn.cursor()
    # a paper small enough to reason about by hand
    paper_id, size = one(cur, """
        SELECT p.paper_id, COUNT(*) n FROM paper p
        JOIN question q ON q.paper_id = p.paper_id AND q.status = 1
        WHERE p.status = 1 GROUP BY p.paper_id HAVING n BETWEEN 4 AND 12
        ORDER BY n LIMIT 1""")
    avg_before = one(cur, 'SELECT avg_score FROM paper WHERE paper_id=%s', (paper_id,))[0]
    print('paper=%s questions=%s avg_before=%s' % (paper_id, size, avg_before))

    code, payload = call('/api/exams/papers/%d/start' % paper_id, token)
    session = payload['data']
    exam_id = session['examId']
    qids = [q['questionId'] for q in session['questions']]
    check('start returns whole paper', len(qids) == size, '%d/%d' % (len(qids), size))

    snap = one(cur, 'SELECT COUNT(*) FROM exam_question WHERE exam_id=%s', (exam_id,))[0]
    check('snapshot written at start', snap == size, '%d rows' % snap)
    score, status = one(cur, 'SELECT score,status FROM exam_score WHERE exam_id=%s', (exam_id,))
    check('score is NULL at start (not 0)', score is None, score)
    check('status ONGOING', status == 'ONGOING', status)
    avg_start = one(cur, 'SELECT avg_score FROM paper WHERE paper_id=%s', (paper_id,))[0]
    check('paper.avg_score untouched by start', avg_start == avg_before,
          '%s -> %s' % (avg_before, avg_start))

    # 4. a question from another paper must be rejected
    foreign = one(cur, 'SELECT question_id FROM question WHERE paper_id<>%s AND status=1 LIMIT 1',
                  (paper_id,))[0]
    code, body = call('/api/exams/%d/submit' % exam_id, token, {
        'answers': [{'questionId': foreign, 'userAnswer': 'A', 'duration': 5}], 'duration': 5})
    check('foreign question rejected', rejected(code, body),
          '%s %s' % (code, str(body)[:160]))

    # 5. answer exactly one question, correctly -> score must be 100/size, not 100
    first_answer = one(cur, 'SELECT answer_snapshot FROM exam_question '
                            'WHERE exam_id=%s AND question_id=%s', (exam_id, qids[0]))[0]
    code, payload = call('/api/exams/%d/submit' % exam_id, token, {
        'answers': [{'questionId': qids[0], 'userAnswer': first_answer, 'duration': 12}],
        'duration': 30})
    check('submit accepted', not rejected(code, payload), str(payload)[:160])
    result = payload['data'] if isinstance(payload, dict) and payload.get('data') else {}
    expected = round(100.0 / size, 6)
    check('denominator is the paper size',
          result.get('totalCount') == size and abs(result.get('score', -1) - expected) < 1e-6,
          'score=%s totalCount=%s answered=%s expected=%s'
          % (result.get('score'), result.get('totalCount'),
             result.get('answeredCount'), round(expected, 2)))

    # 6. duplicate submit
    code, body = call('/api/exams/%d/submit' % exam_id, token, {
        'answers': [{'questionId': qids[0], 'userAnswer': first_answer, 'duration': 12}],
        'duration': 30})
    check('duplicate submit rejected', rejected(code, body),
          '%s %s' % (code, str(body)[:160]))

    pass
    rows = list(one_all(cur, 'SELECT is_correct,score_rate FROM answer_record WHERE exam_id=%s',
                        (exam_id,)))
    check('objective score_rate is NULL', rows and all(r[1] is None for r in rows), rows)
    check('is_correct recorded', rows and rows[0][0] in (0, 1, True, False), rows)

    score, status = one(cur, 'SELECT score,status FROM exam_score WHERE exam_id=%s', (exam_id,))
    check('exam_score persisted', status == 'SUBMITTED' and abs(score - expected) < 1e-6,
          '%s %s' % (score, status))
    avg_end = one(cur, 'SELECT avg_score FROM paper WHERE paper_id=%s', (paper_id,))[0]
    check('paper.avg_score updated only after submit', avg_end is not None and avg_end > 0,
          '%s -> %s' % (avg_start, avg_end))
    conn.close()

    failed = [name for name, ok, _ in checks if not ok]
    print('\n%d/%d checks passed' % (len(checks) - len(failed), len(checks)))
    if failed:
        print('FAILED: ' + ', '.join(failed))
    return 1 if failed else 0


def one_all(cur, sql, args=()):
    cur.execute(sql, args)
    return cur.fetchall()


if __name__ == '__main__':
    raise SystemExit(main())
