"""Conservative same-site conflict candidates. Runtime plugins/slices can eliminate overlap."""
import collections
import fnmatch
import itertools
import json
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding='utf-8')
root = Path(sys.argv[1] if len(sys.argv) > 1 else 'build/mixin-audit')
data = json.loads((root / 'inventory.json').read_text(encoding='utf-8'))
by_target = collections.defaultdict(list)

def array(value):
    return value if isinstance(value, list) else [value] if value is not None else []

def kind(annotation):
    return annotation['annotation'].rsplit('/', 1)[-1].rstrip(';')

def method_name(selector):
    return selector.split('(', 1)[0].rsplit(';', 1)[-1]

for mixin in data['mixins']:
    target = mixin.get('target', {})
    targets = array(target.get('value')) + array(target.get('targets'))
    for method in mixin['methods']:
        annotations = method['annotations']
        for annotation in annotations:
            k = kind(annotation)
            if k in ('Overwrite',):
                selectors = [method['name'] + method['descriptor']]
            elif 'method' in annotation and k not in ('TargetHandler',):
                selectors = array(annotation['method'])
            else:
                continue
            item = dict(mixin=mixin['mixin'], config=mixin['config'], jar=mixin['jar'], side=mixin['side'],
                        declared=mixin['declaredInMetadata'], plugin=mixin['plugin'], hash=mixin['sha256'],
                        priority=target.get('priority', 1000), require=annotation.get('require', mixin['defaultRequire']),
                        kind=k, handler=method['name'], selectors=selectors, annotation=annotation)
            for t in targets:
                by_target[t.replace('/', '.')].append(item)
        # Public/protected plain methods can implicitly overwrite inherited target methods.
        control_annotations = {'Unique', 'Shadow', 'Accessor', 'Invoker', 'Overwrite', 'Inject', 'Redirect',
                               'ModifyArg', 'ModifyArgs', 'ModifyConstant', 'ModifyVariable', 'ModifyExpressionValue',
                               'WrapOperation', 'WrapMethod', 'WrapWithCondition', 'ModifyReturnValue', 'TargetHandler'}
        if not any(kind(a) in control_annotations for a in annotations) and method['access'] & 5:
            item = dict(mixin=mixin['mixin'], config=mixin['config'], jar=mixin['jar'], side=mixin['side'],
                        declared=mixin['declaredInMetadata'], plugin=mixin['plugin'], hash=mixin['sha256'],
                        priority=target.get('priority', 1000), require=0, kind='Merge', handler=method['name'],
                        selectors=[method['name'] + method['descriptor']], annotation={})
            for t in targets:
                by_target[t.replace('/', '.')].append(item)

def methods_overlap(a, b):
    for x, y in itertools.product(a['selectors'], b['selectors']):
        nx, ny = method_name(x), method_name(y)
        if not (fnmatch.fnmatchcase(nx, ny) or fnmatch.fnmatchcase(ny, nx)):
            continue
        if '(' in x and '(' in y and x.split('(', 1)[1] != y.split('(', 1)[1]:
            continue
        return True
    return False

def points(item):
    a = item['annotation']
    if item['kind'] == 'ModifyConstant':
        return [('CONSTANT', json.dumps({k: v for k, v in c.items() if k not in ('annotation', 'ordinal', 'slice')}, sort_keys=True), c.get('ordinal', -1), -1, '')
                for c in array(a.get('constant', {}))]
    return [(p.get('value'), p.get('target', ''), p.get('ordinal', -1), p.get('opcode', -1),
             next((arg for arg in p.get('args', []) if arg.startswith('array=')), '')) for p in array(a.get('at'))]

def points_overlap(a, b):
    return any(x[:2] == y[:2] and (x[2] < 0 or y[2] < 0 or x[2] == y[2])
               and (x[3] < 0 or y[3] < 0 or x[3] == y[3]) and (not x[4] or not y[4] or x[4] == y[4])
               for x, y in itertools.product(points(a), points(b)))

candidates = []
for target, items in sorted(by_target.items()):
    for a, b in itertools.combinations(items, 2):
        if (a['mixin'], a['hash'], a['handler']) == (b['mixin'], b['hash'], b['handler']):
            continue
        if a['side'] == 'client' and b['side'] == 'server' or a['side'] == 'server' and b['side'] == 'client':
            continue
        if not methods_overlap(a, b):
            continue
        ak, bk = a['kind'], b['kind']
        merge = {'Overwrite', 'Merge'}
        risk = None
        if ak in merge and bk in merge:
            risk = 'overwrite-overlap'
        elif (ak in merge) != (bk in merge):
            injector = b if ak in merge else a
            if injector['kind'] not in ('Inject',) or any(p[0] not in ('HEAD', 'RETURN', 'TAIL') for p in points(injector)):
                risk = 'overwrite-injection'
        elif ak == bk == 'Redirect' and points_overlap(a, b):
            risk = 'redirect-overlap'
        elif ak == bk == 'ModifyConstant' and points_overlap(a, b):
            risk = 'constant-overlap'
        elif 'Redirect' in (ak, bk) and points_overlap(a, b):
            other = b if ak == 'Redirect' else a
            if other['kind'] not in ('ModifyExpressionValue', 'WrapOperation', 'WrapWithCondition'):
                risk = 'redirect-injection'
        if risk:
            candidates.append(dict(target=target, risk=risk, bothDeclared=a['declared'] and b['declared'], a=a, b=b))

for i, c in enumerate(candidates, 1):
    c['id'] = f'MX-{i:04d}'
(root / 'candidates.json').write_text(json.dumps(candidates, ensure_ascii=False, indent=2), encoding='utf-8')
print('Total conservative candidates:', len(candidates), dict(collections.Counter(c['risk'] for c in candidates)))
print('Both configs declared in metadata:', sum(c['bothDeclared'] for c in candidates))
for c in candidates:
    if c['risk'] not in ('redirect-overlap', 'constant-overlap') or not c['bothDeclared']:
        continue
    print(c['id'], c['risk'], c['target'], '|', c['a']['config'], c['a']['handler'], '|', c['b']['config'], c['b']['handler'])
