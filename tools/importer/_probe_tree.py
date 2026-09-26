"""列出题库源目录结构，用于确认实际文件布局（不依赖文件名推断）。"""
import os

BASE = r'D:\复刻lk'
TARGETS = [
    '2011-2025国考行测真题',
    '2011-2025国考申论真题',
    '国考历年真题试卷（2000-2024带解析版）',
    '行测5000题【2024年6月版次】',
]

for t in TARGETS:
    p = os.path.join(BASE, t)
    print('=' * 70)
    print(t, '| exists:', os.path.isdir(p))
    if not os.path.isdir(p):
        continue
    for root, dirs, files in os.walk(p):
        rel = os.path.relpath(root, p)
        if rel.count(os.sep) > 1:
            continue
        print(f'  [{rel}]  {len(files)} files')
        for f in sorted(files)[:8]:
            print('       ', f)
        if len(files) > 8:
            print(f'        ... +{len(files) - 8} more')
