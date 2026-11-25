"""Writes app/src/test/resources/assistant-validate.tsv: (question tokens, program, valid) from Python's validator."""
import base64
import json
import random
import sys
from pathlib import Path
from assistant_validate import valid

run = Path(sys.argv[1])
data = json.loads((run / 'dataset.json').read_text())
rng = random.Random(5)
rows = rng.sample(data['dev'], 150) + rng.sample(data['test'], 150)
cases = []
for row in rows:
    cases.append((row['source'], row['program']))
    broken = list(row['program'])
    if len(broken) > 3:
        del broken[rng.randrange(len(broken) - 2)]
    cases.append((row['source'], broken))
cases += [(['who', 'can', 'see', 'my', 'expenses', '?'], ['help', 'privacy', ';', 'answer']),
          (['how', 'do', 'i', 'import', 'a', 'pdf', 'bill'], ['help', 'export', ';', 'answer']),
          (['CAT1', 'as', 'a', 'percentage', 'of', 'total', 'this', 'week'], ['spend', 'this_week', 'cat', 'CAT1', ';', 'answer'])]
out = Path(__file__).resolve().parents[2] / 'app/src/test/resources/assistant-validate.tsv'
out.write_text('\n'.join('\t'.join((base64.b64encode(' '.join(s).encode()).decode(), base64.b64encode(' '.join(p).encode()).decode(),
                                     str(valid(s, p)).lower())) for s, p in cases) + '\n')
print(len(cases), sum(valid(s, p) for s, p in cases))
