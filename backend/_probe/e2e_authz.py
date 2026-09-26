"""Live checks for the four backend hardening fixes.

  1. role-annotation auth: a user token is rejected from @RequireManager endpoints
  2. id-space isolation: a manager token is rejected from user-only endpoints
  3. admin question search is paginated (PageResult envelope)
  4. update validation parity: an illegal answer on update is rejected

Uses the manager account from application config; the manager password is read
from the env (never hard-coded).  A throwaway user is registered for the user
side.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from e2e_exam import call, check, checks, rejected  # noqa: E402


def login(kind, name, password):
    _, payload = call('/api/auth/%s/login' % kind, body={'userName': name, 'password': password})
    data = payload.get('data') if isinstance(payload, dict) else None
    return (data or {}).get('token')


def main():
    user = 'authz_%d' % os.getpid()
    call('/api/auth/user/register', body={
        'userName': user, 'password': 'Test@12345', 'email': user + '@example.com'})
    user_token = login('user', user, 'Test@12345')
    check('user login', bool(user_token))

    mgr_name = os.getenv('HONGHU_ADMIN_USER', 'admin')
    mgr_pass = os.getenv('HONGHU_ADMIN_PASSWORD')
    manager_token = login('manager', mgr_name, mgr_pass) if mgr_pass else None
    check('manager login', bool(manager_token),
          'set HONGHU_ADMIN_USER/HONGHU_ADMIN_PASSWORD' if not manager_token else '')

    # 1. user token must be refused by an @RequireManager endpoint
    code, body = call('/api/admin/questions?pageNum=1&pageSize=5', user_token, method='GET')
    check('user token rejected from admin endpoint', rejected(code, body),
          '%s %s' % (code, str(body)[:120]))

    if manager_token:
        # 2. manager token must be refused by a user-only endpoint (requireUserId)
        code, body = call('/api/exams/history', manager_token, method='GET')
        check('manager token rejected from user endpoint', rejected(code, body),
              '%s %s' % (code, str(body)[:120]))

        # 3. admin search returns a paginated envelope
        code, body = call('/api/admin/questions?pageNum=1&pageSize=5', manager_token, method='GET')
        data = body.get('data') if isinstance(body, dict) else None
        ok = (isinstance(data, dict) and 'total' in data and 'list' in data
              and isinstance(data.get('list'), list) and len(data['list']) <= 5
              and data.get('pageSize') == 5)
        check('admin search is paginated', ok,
              'total=%s returned=%s pageSize=%s' % (
                  (data or {}).get('total'), len((data or {}).get('list') or []),
                  (data or {}).get('pageSize')))

        # 4. update with an illegal answer is rejected (validation parity)
        first = (data or {}).get('list') or []
        if first:
            qid = first[0]['questionId']
            code, body = call('/api/admin/questions/%d' % qid, manager_token,
                              {'questionStem': 'x', 'answer': 'Z'}, method='PUT')
            check('update rejects illegal answer', rejected(code, body),
                  '%s %s' % (code, str(body)[:120]))
        else:
            check('update rejects illegal answer', False, 'no question to edit')

    failed = [name for name, ok, _ in checks if not ok]
    print('\n%d/%d checks passed' % (len(checks) - len(failed), len(checks)))
    if failed:
        print('FAILED: ' + ', '.join(failed))
    return 1 if failed else 0


if __name__ == '__main__':
    raise SystemExit(main())
