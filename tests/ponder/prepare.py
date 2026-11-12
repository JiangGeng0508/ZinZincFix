"""Build disposable fixtures from installed bytes; never rewrite installed mods."""
import io
import json
import sys
import zipfile
from pathlib import Path

root, output, mode = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]
output.mkdir(parents=True, exist_ok=True)
found = {}

def scan(z):
    names = z.namelist()
    for key, marker in [('simulated', 'simulated.mixins.json'), ('ponderer', 'ponderer.mixins.json'),
                        ('ponder', 'net/createmod/ponder/foundation/PonderScene.class')]:
        if marker in names:
            found[key] = {n: z.read(n) for n in names if not n.endswith('/')}
    for n in names:
        if n.endswith('.jar'):
            with zipfile.ZipFile(io.BytesIO(z.read(n))) as sub:
                scan(sub)

for path in sorted(root.glob('*.jar')):
    with zipfile.ZipFile(path) as jar:
        scan(jar)
assert len(found) == 3, found.keys()
for key, entries in found.items():
    with zipfile.ZipFile(output / (key + '-original.jar'), 'w', zipfile.ZIP_DEFLATED) as jar:
        for n, data in entries.items():
            jar.writestr(n, data)

selected = {
    'simulated': ['ponder.PonderSceneMixin', 'ponder.PonderSceneTransformMixin'],
    'ponderer': ['PonderSceneAccessor', 'PonderSceneViewOffsetMixin', 'SceneTransformOffsetMixin', 'PonderUIAccessor'],
}
active = [] if mode == 'absent' else ['ponder'] + (['simulated', 'ponderer'] if mode in ('both', 'baseline') else [mode])
with zipfile.ZipFile(output / 'fixture.jar', 'w', zipfile.ZIP_DEFLATED) as jar:
    jar.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nFMLModType: GAMELIBRARY\nAutomatic-Module-Name: ponder.smoke.fixture\n\n')
    for key in active:
        for n, data in found[key].items():
            if n.endswith('.class') or n.startswith('META-INF/services/'):
                jar.writestr(n, data)
resources = output / 'resources'
resources.mkdir(exist_ok=True)
(resources / 'META-INF').mkdir(exist_ok=True)
(resources / 'META-INF/accesstransformer.cfg').write_bytes(found['ponder']['META-INF/accesstransformer.cfg'])
for key, mixins in selected.items():
    original = json.loads(found[key][key + '.mixins.json'])
    original['client'] = mixins if key in active else []
    original['mixins'] = []
    original['server'] = []
    original.pop('refmap', None)  # Named development runtime; target mod/Ponder names are unchanged.
    (resources / (key + '.mixins.json')).write_text(json.dumps(original), encoding='utf-8')
print('Prepared original-byte fixtures:', mode)
