import collections
import gzip
import hashlib
import json
import re
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding='utf-8')
game, output = Path(sys.argv[1]), Path(sys.argv[2])
output.mkdir(parents=True, exist_ok=True)
paths = sorted((game / 'logs').glob('*.log*')) + sorted((game / 'crash-reports').glob('*.txt'))
if Path('RawOutput.txt').exists():
    paths.append(Path('RawOutput.txt'))
pattern = re.compile(r'@\w+ conflict|Method overwrite conflict|Critical injection failure|InvalidInjectionException:|InjectionError:|Mixin apply.*failed|Mixin target.*not found|cannot inject into.*merged')
applied_pattern = re.compile(r'Mixing (\S+) from (\S+) into (\S+)')
findings, sources, applied, selected, skips = {}, [], {}, set(), []
for path in paths:
    raw = path.read_bytes()
    sources.append(dict(file=str(path), bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()))
    text = (gzip.decompress(raw) if path.suffix == '.gz' else raw).decode('utf-8', errors='replace')
    for number, line in enumerate(text.splitlines(), 1):
        match = pattern.search(line)
        if match:
            message = line[match.start():]
            row = findings.setdefault(message, dict(message=message, occurrences=[]))
            row['occurrences'].append(dict(file=str(path), line=number))
        match = applied_pattern.search(line)
        if match:
            key = '|'.join(match.groups())
            applied.setdefault(key, dict(mixin=match[1], config=match[2], target=match[3], evidence=dict(file=str(path), line=number)))
        if 'Selecting config ' in line:
            selected.add(line.split('Selecting config ', 1)[1].strip())
        if 'Skipping mixin ' in line or 'Force-disabling mixin ' in line:
            skips.append(dict(file=str(path), line=number, text=line))
result = dict(sources=sources, findings=list(findings.values()), applied=list(applied.values()), selectedConfigs=sorted(selected), skipped=skips)
(output / 'log-review.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print('Log files', len(sources), 'unique findings', len(findings), 'applied tuples', len(applied))
for row in findings.values():
    if 'was not found' not in row['message']:
        print(row['message'][:900])
