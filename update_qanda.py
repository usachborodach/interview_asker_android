import os
import json

SOURCE_PATH = '/home/user/repos/ShuraRepo/preparing/questions.md'
QANDA_PATH = '/home/user/repos/interview_asker_android/app/src/main/assets/qanda.json'

base_path = os.path.dirname(__file__)
os.chdir(base_path)

with open(SOURCE_PATH) as file:
    content = file.read()

items = content.split('\n\n---\n\n')
res = list()
for item in items:
    splitted_item = item.split('\n', 1)
    res.append({
        'question': splitted_item[0],
        'answer': splitted_item[1]
    })

with open(QANDA_PATH, 'w') as file:
    json.dump(res, file, ensure_ascii=False, indent=4)