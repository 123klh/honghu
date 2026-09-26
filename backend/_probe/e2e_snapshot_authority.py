"""Prove the exam snapshot, not the live question row, decides the score.

Scenario: user starts an exam, an admin then edits the correct answer of one
question, the user submits the answer that was correct *at start*.  With the
snapshot in force the user must still be marked correct.

Also exercises the three V4 recalc procedures so their bodies are validated by
MySQL rather than assumed to be well-formed.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from e2e_exam import call, check, checks, db, one, rejected  # noqa: E402


def main():
    user = 'snap_%d' % os.getpid()
    call('/api/auth/user/register', body={
        'userName': user, 'password': 'Test@12345', 'email': user + '@example.com'})
    _, payload = call('/api/auth/user/login', body={
        'userName': user, 'password': 'Test@12345'})
    token = payload['data']['token']

    conn = db()
    cur = conn.cursor()
    paper_id, size = one(cur, """
        SELECT p.paper_id, COUNT(*) n FROM paper p
        JOIN question q ON q.paper_id = p.paper_id AND q.status = 1
        WHERE p.status = 1 GROUP BY p.paper_id HAVING n BETWEEN 4 AND 12
        ORDER BY n LIMIT 1""")

    _, payload = call('/api/exams/papers/%d/start' % paper_id, token)
    exam_id = payload['data']['examId']
    qid = payload['data']['questions'][0]['questionId']

    snapshot_answer = one(cur, 'SELECT answer_snapshot FROM exam_question '
                               'WHERE exam_id=%s AND question_id=%s', (exam_id, qid))[0]
    original = one(cur, 'SELECT answer FROM question WHERE question_id=%s', (qid,))[0]
    check('snapshot captured the answer at start', snapshot_answer == original,
          '%s vs %s' % (snapshot_answer, original))

    # An admin edits the correct answer while the exam is in flight.
    tampered = 'D' if original.upper() != 'D' else 'A'
    cur.execute('UPDATE question SET answer=%s WHERE question_id=%s', (tampered, qid))
    try:
        _, payload = call('/api/exams/%d/submit' % exam_id, token, {
            'answers': [{'questionId': qid, 'userAnswer': snapshot_answer, 'duration': 10}],
            'duration': 20})
        result = payload.get('data') or {}
        check('answer changed mid-exam does not change grading',
              result.get('correctCount') == 1,
              'live answer now %s, graded correctCount=%s' % (tampered, result.get('correctCount')))
        graded = (result.get('answers') or [{}])[0].get('correctAnswer')
        check('result reports the snapshot answer', graded == snapshot_answer,
              '%s' % graded)
    finally:
        # restore the question bank: this is real imported data, not fixture data
        cur.execute('UPDATE question SET answer=%s WHERE question_id=%s', (original, qid))
        restored = one(cur, 'SELECT answer FROM question WHERE question_id=%s', (qid,))[0]
        check('question bank restored', restored == original, restored)

    for proc in ('sp_recalc_paper_questions_count', 'sp_recalc_paper_avg_score',
                 'sp_recalc_question_accuracy'):
        try:
            cur.execute('CALL %s()' % proc)
            check('procedure runs: ' + proc, True)
        except Exception as exc:  # noqa: BLE001 - report, do not hide
            check('procedure runs: ' + proc, False, '%s: %s' % (type(exc).__name__, exc))

    conn.close()
    failed = [name for name, ok, _ in checks if not ok]
    print('\n%d/%d checks passed' % (len(checks) - len(failed), len(checks)))
    if failed:
        print('FAILED: ' + ', '.join(failed))
    return 1 if failed else 0


if __name__ == '__main__':
    raise SystemExit(main())
