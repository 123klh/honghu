"""把源目录真实路径写成 UTF-8 清单，避免凭猜测拼路径。"""
import io
import os

BASE = r'D:\复刻lk'
here = os.path.dirname(os.path.abspath(__file__))
out = io.open(os.path.join(here, '_probe_paths.txt'), 'w', encoding='utf-8')

root = os.path.join(BASE, '国考历年真题试卷（2000-2024带解析版）')
out.write('root exists: %s\n' % os.path.isdir(root))
for dirpath, dirnames, filenames in os.walk(root):
    rel = os.path.relpath(dirpath, BASE)
    pdfs = [f for f in filenames if f.lower().endswith('.pdf')]
    out.write('DIR: %s  (pdfs=%d)\n' % (rel, len(pdfs)))
    for f in sorted(pdfs)[:3]:
        out.write('     e.g. %s\n' % f)
out.close()
