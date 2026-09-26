set -e
B=http://localhost:8080
U="e2e_$RANDOM"
echo "== register $U"
curl -s -X POST $B/api/auth/user/register -H 'Content-Type: application/json' \
  -d "{\"userName\":\"$U\",\"password\":\"Test@12345\",\"email\":\"$U@example.com\"}"; echo
echo "== login"
TOK=$(curl -s -X POST $B/api/auth/user/login -H 'Content-Type: application/json' \
  -d "{\"userName\":\"$U\",\"password\":\"Test@12345\"}" | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
echo "token len ${#TOK}"
echo "== pick a small paper"
PID=$(python -c "
import pymysql,os
c=pymysql.connect(host='127.0.0.1',user='root',password=os.getenv('HONGHU_DB_PASSWORD','1234'),database='honghu',charset='utf8mb4')
cur=c.cursor();cur.execute(\"SELECT p.paper_id,COUNT(*) n FROM paper p JOIN question q ON q.paper_id=p.paper_id AND q.status=1 WHERE p.status=1 GROUP BY p.paper_id HAVING n BETWEEN 3 AND 10 ORDER BY n LIMIT 1\")
r=cur.fetchone();print(r[0] if r else '');c.close()")
echo "paper=$PID"
echo "== avg_score before"
python -c "
import pymysql,os
c=pymysql.connect(host='127.0.0.1',user='root',password=os.getenv('HONGHU_DB_PASSWORD','1234'),database='honghu',charset='utf8mb4')
cur=c.cursor();cur.execute('SELECT avg_score FROM paper WHERE paper_id=%s',($PID,));print('avg_before',cur.fetchone());c.close()"
echo "== start"
curl -s -X POST $B/api/exams/papers/$PID/start -H "Authorization: Bearer $TOK" > /tmp/sess.json
python -c "
import json;d=json.load(open('/tmp/sess.json'))['data']
print('examId',d['examId'],'questions',len(d['questions']))
open('/tmp/exam_id','w').write(str(d['examId']))
open('/tmp/qids','w').write(','.join(str(q['questionId']) for q in d['questions']))"
EXAM=$(cat /tmp/exam_id); QIDS=$(cat /tmp/qids)
echo "== snapshot rows + score after start"
python -c "
import pymysql,os
c=pymysql.connect(host='127.0.0.1',user='root',password=os.getenv('HONGHU_DB_PASSWORD','1234'),database='honghu',charset='utf8mb4')
cur=c.cursor()
cur.execute('SELECT COUNT(*) FROM exam_question WHERE exam_id=%s',($EXAM,));print('snapshot_rows',cur.fetchone()[0])
cur.execute('SELECT score,status FROM exam_score WHERE exam_id=%s',($EXAM,));print('exam_score',cur.fetchone())
cur.execute('SELECT avg_score FROM paper WHERE paper_id=%s',($PID,));print('avg_after_start',cur.fetchone())
c.close()"
echo "== submit only 1 of N, correct"
python -c "
import json,urllib.request,pymysql,os
qids=[int(x) for x in open('/tmp/qids').read().split(',')]
c=pymysql.connect(host='127.0.0.1',user='root',password=os.getenv('HONGHU_DB_PASSWORD','1234'),database='honghu',charset='utf8mb4')
cur=c.cursor();cur.execute('SELECT answer_snapshot FROM exam_question WHERE exam_id=%s AND question_id=%s',($EXAM,qids[0]))
ans=cur.fetchone()[0];c.close()
body=json.dumps({'answers':[{'questionId':qids[0],'userAnswer':ans,'duration':12}],'duration':30}).encode()
req=urllib.request.Request('$B/api/exams/$EXAM/submit',data=body,headers={'Content-Type':'application/json','Authorization':'Bearer $TOK'})
print(urllib.request.urlopen(req).read().decode()[:400])"
echo "== duplicate submit must fail"
python -c "
import json,urllib.request,urllib.error
qids=[int(x) for x in open('/tmp/qids').read().split(',')]
body=json.dumps({'answers':[{'questionId':qids[0],'userAnswer':'A','duration':12}],'duration':30}).encode()
req=urllib.request.Request('$B/api/exams/$EXAM/submit',data=body,headers={'Content-Type':'application/json','Authorization':'Bearer $TOK'})
try: print('UNEXPECTED OK',urllib.request.urlopen(req).read().decode()[:200])
except urllib.error.HTTPError as e: print('rejected',e.code,e.read().decode()[:200])"
echo "== score_rate must be NULL for objective"
python -c "
import pymysql,os
c=pymysql.connect(host='127.0.0.1',user='root',password=os.getenv('HONGHU_DB_PASSWORD','1234'),database='honghu',charset='utf8mb4')
cur=c.cursor()
cur.execute('SELECT is_correct,score_rate FROM answer_record WHERE exam_id=%s',($EXAM,));print('answer_record',cur.fetchall())
cur.execute('SELECT score,status FROM exam_score WHERE exam_id=%s',($EXAM,));print('exam_score',cur.fetchone())
cur.execute('SELECT avg_score FROM paper WHERE paper_id=%s',($PID,));print('avg_after_submit',cur.fetchone())
c.close()"
