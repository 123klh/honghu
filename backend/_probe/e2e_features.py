"""Live checks for the new feature layers: favorites, community, shenlun, stats.

Uses a throwaway user; picks real question/paper/fr_question IDs from the DB.
Shenlun scoring is checked against a real rubric: copying the reference answer
must score high, off-topic text must score low.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from e2e_exam import call, check, checks, db, one, rejected  # noqa: E402


def main():
    user = 'feat_%d' % os.getpid()
    call('/api/auth/user/register', body={
        'userName': user, 'password': 'Test@12345', 'email': user + '@example.com'})
    _, payload = call('/api/auth/user/login', body={'userName': user, 'password': 'Test@12345'})
    token = payload['data']['token']

    conn = db()
    cur = conn.cursor()
    qid = one(cur, "SELECT question_id FROM question WHERE status=1 LIMIT 1")[0]
    paper_id = one(cur, "SELECT paper_id FROM paper WHERE status=1 LIMIT 1")[0]
    fr = one(cur, """SELECT r.fr_question_id FROM shenlun_rubric r
                     GROUP BY r.fr_question_id HAVING COUNT(*)>=2 LIMIT 1""")
    fr_id = fr[0]

    # ---- favorites ----
    code, body = call('/api/favorites/questions/%d' % qid, token, {'folder': '常错', 'notes': 'x'})
    check('favorite a question', not rejected(code, body), str(body)[:100])
    code, body = call('/api/favorites/questions', token, method='GET')
    favs = (body.get('data') or []) if isinstance(body, dict) else []
    check('favorite appears in list', any(f['questionId'] == qid for f in favs), len(favs))
    # idempotent re-add
    call('/api/favorites/questions/%d' % qid, token, {'folder': '重要', 'notes': 'y'})
    code, body = call('/api/favorites/questions', token, method='GET')
    favs = body.get('data') or []
    check('re-favorite is idempotent (no dup)',
          sum(1 for f in favs if f['questionId'] == qid) == 1, len(favs))
    call('/api/favorites/papers/%d' % paper_id, token, {})
    code, body = call('/api/favorites/papers', token, method='GET')
    check('favorite a paper', any(f['paperId'] == paper_id for f in (body.get('data') or [])))
    # remove
    call('/api/favorites/questions/%d' % qid, token, method='DELETE')
    code, body = call('/api/favorites/questions', token, method='GET')
    check('unfavorite removes it',
          not any(f['questionId'] == qid for f in (body.get('data') or [])))

    # ---- community ----
    code, body = call('/api/community/discussions', token,
                      {'title': '如何提高行测速度', 'content': '求方法', 'questionType': 'NONE'})
    did = (body.get('data') or {}).get('discussionId')
    check('create discussion', did is not None, str(body)[:100])
    code, body = call('/api/community/discussions/%d/replies' % did, token, {'content': '多刷题多总结'})
    rid = (body.get('data') or {}).get('replyId')
    check('reply to discussion', rid is not None, str(body)[:100])
    code, body = call('/api/community/replies/%d/accept' % rid, token, {})
    check('author can accept reply', not rejected(code, body), str(body)[:100])
    code, body = call('/api/community/discussions/%d' % did, token, method='GET')
    data = body.get('data') or {}
    replies = data.get('replies') or []
    check('detail shows accepted reply',
          replies and replies[0].get('isAccepted') in (1, True), replies)
    code, body = call('/api/community/discussions?pageNum=1&pageSize=5', token, method='GET')
    d = body.get('data') or {}
    check('discussion list paginated', 'total' in d and isinstance(d.get('list'), list),
          str(d)[:80])

    # ---- shenlun scoring ----
    ref = one(cur, "SELECT GROUP_CONCAT(point_text SEPARATOR '') FROM shenlun_rubric "
                   "WHERE fr_question_id=%s", (fr_id,))[0]
    code, body = call('/api/shenlun/questions/%d/score' % fr_id, token, {'answer': ref})
    rep = body.get('data') or {}
    check('copying reference scores high', rep.get('scoreRate', 0) >= 0.8,
          'rate=%s hit=%s/%s' % (rep.get('scoreRate'), rep.get('hitPoints'), rep.get('totalPoints')))
    check('score report has per-point breakdown',
          isinstance(rep.get('pointResults'), list) and len(rep['pointResults']) >= 2,
          len(rep.get('pointResults') or []))
    check('score report has dimensions',
          isinstance(rep.get('dimensions'), list) and len(rep['dimensions']) == 3,
          rep.get('dimensions'))
    code, body = call('/api/shenlun/questions/%d/score' % fr_id, token,
                      {'answer': '今天天气很好我去公园散步看花看草心情愉快极了真是美好的一天啊'})
    off = body.get('data') or {}
    check('off-topic scores low', off.get('scoreRate', 1) <= 0.3, 'rate=%s' % off.get('scoreRate'))

    # ---- statistics ----
    code, body = call('/api/stats/me?days=30', token, method='GET')
    st = body.get('data') or {}
    check('user stats returns shape',
          'totalAnswered' in st and 'subjectBreakdown' in st and 'dailyActivity' in st,
          str(st)[:100])

    conn.close()
    failed = [name for name, ok, _ in checks if not ok]
    print('\n%d/%d checks passed' % (len(checks) - len(failed), len(checks)))
    if failed:
        print('FAILED: ' + ', '.join(failed))
    return 1 if failed else 0


if __name__ == '__main__':
    raise SystemExit(main())
