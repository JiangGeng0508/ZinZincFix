"""Attach observed application evidence and explicit review decisions to every candidate."""
import collections
import json
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding='utf-8')
root = Path('build/mixin-audit')
inventory = json.loads((root / 'inventory.json').read_text(encoding='utf-8'))
logs = json.loads((root / 'log-review.json').read_text(encoding='utf-8'))
candidates = json.loads((root / 'candidates.json').read_text(encoding='utf-8'))
packages = {c['config']: c['settings'].get('package', '') for c in inventory['configs']}
applied = {(packages.get(a['config'], '') + '.' + a['mixin'], a['config'], a['target']) for a in logs['applied']}
def review(c):
    pair = {c['a']['config'], c['b']['config']}
    if pair == {'simulated.mixins.json', 'ponderer.mixins.json'}:
        return '已修复：1.0.6 组合缩放'
    if pair == {'northstar.mixins.json', 'spelunkery-common.mixins.json'} and c['target'].endswith('.IceBlock'):
        return '已有修复：Northstar 优先级 Coremod'
    if pair == {'deployer.mixins.json', 'fluidlogistics.mixins.json'}:
        return '确认功能覆盖：Deployer 克隆物品分支被跳过'
    if pair == {'moonlight-common.mixins.json', 'supplementaries-common.mixins.json'}:
        return '确认功能覆盖：Moonlight 槽位图标 tick 被跳过'
    if pair <= {'lithium.mixins.json', 'modernfix-modernfix.mixins.json', 'saturn.mixins.json'} and c['target'].endswith(('.Biome', '.PalettedContainer', '$InputContainer', '$EmptyContainer', '$OutputContainer')):
        return '重复优化；部分成员有跳过日志'
    if pair == {'sable_sa_compat.mixins.json'}:
        return '有意覆盖：第 9、19 个常量保留 0.5'
    if pair == {'client.voxy.mixins.json', 'fakesight.voxy.mixins.json'}:
        return '排除当前版本：Sodium 无旧调用点；两者 require=0'
    if c['risk'] == 'redirect-overlap' and pair in ({'mixins.iris.json'}, {'xaeroplus.mixins.json'}):
        return '不同 slice；无此注入竞争日志'
    if any(x.startswith(('fabric-asyncparticles', 'fabric-badoptimizations')) for x in pair):
        return '含 Fabric 平台副本；不是两套 NeoForge 实现同时加载的证据'
    return '静态候选；未证明存在运行时冲突'

counts = collections.Counter()
lines = ['# Mixin 候选明细（2026-11-12）', '',
         '这是保守扫描结果，不是 387 个已确认故障。读取/写入 opcode、数组 get/set、方法描述符和 ordinal 已做初步区分；运行时插件、slice、动态目标及其他转换器仍可能消除或引入重叠。', '',
         '“双方曾应用”表示现有日志分别记录过两个 Mixin 应用到此目标，不保证来自同一会话，也不证明具体 handler 成功执行。未记录也不等于被禁用。扫描的是磁盘原始字节码，已有 Coremod 修复另行标记。', '',
         '| ID | 类型 | 目标类 | A（配置 / handler） | B（配置 / handler） | 双方曾应用 | 复核结论 |',
         '|---|---|---|---|---|---|---|']
for c in candidates:
    for side in ('a', 'b'):
        a = c[side]
        a['observedApplied'] = (a['mixin'], a['config'], c['target']) in applied
    both = c['a']['observedApplied'] and c['b']['observedApplied']
    c['review'] = review(c)
    counts[c['review']] += 1
    cells = [c['id'], c['risk'], c['target'],
             c['a']['config'] + ' / ' + c['a']['handler'], c['b']['config'] + ' / ' + c['b']['handler'],
             '是' if both else '未同时确认', c['review']]
    lines.append('| ' + ' | '.join(s.replace('|', '\\|').replace('<', '&lt;') for s in cells) + ' |')
(root / 'reviewed-candidates.json').write_text(json.dumps(candidates, ensure_ascii=False, indent=2), encoding='utf-8')
Path('docs/mixin-audit-candidates-2026-11-12.zh-CN.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
print('Reviewed categories:', json.dumps(counts, ensure_ascii=False))
print('Both observed:', sum(c['a']['observedApplied'] and c['b']['observedApplied'] for c in candidates))
