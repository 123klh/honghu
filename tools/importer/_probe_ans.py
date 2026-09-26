import io
import os
import warnings

warnings.filterwarnings('ignore')
import pdfplumber

BASE = r'D:\复刻lk'
D2 = os.path.join(BASE, '国考历年真题试卷（2000-2024带解析版）',
                  '2000-2024国考行测PDF（带解析版）', '行测-答案及解析')

here = os.path.dirname(os.path.abspath(__file__))
out = io.open(os.path.join(here, '_probe_ans.txt'), 'w', encoding='utf-8')

out.write('dir exists: %s\n' % os.path.isdir(D2))
files = sorted(f for f in os.listdir(D2) if f.lower().endswith('.pdf'))
out.write('ALL FILES (%d):\n' % len(files))
for f in files:
    out.write('  ' + f + '\n')

for pick in [f for f in files if f.startswith(('2022', '2023', '2019'))]:
    p = os.path.join(D2, pick)
    with pdfplumber.open(p) as pdf:
        txt = '\n'.join((pg.extract_text() or '') for pg in pdf.pages[:3])
    out.write('=' * 78 + '\n' + pick + '\nchars(3pg)=' + str(len(txt)) + '\n' + '-' * 78 + '\n')
    out.write(txt[:2600] + '\n')

out.close()
