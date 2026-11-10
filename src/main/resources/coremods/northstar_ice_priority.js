function initializeCoreMod() {
    var ASMAPI = Java.type('net.neoforged.coremod.api.ASMAPI');

    return {
        'northstar_ice_priority': {
            'target': {
                'type': 'CLASS',
                'name': 'com.lightning.northstar.mixin.block.IceBlockMixin'
            },
            'transformer': function (node) {
                // Spelunkery 0.4.4 merges melt() at priority 1000. Northstar 0.6.6
                // injects into it at the same priority, which INVOKE disallows.
                // Change the mixin annotation before Mixin reads it; the config's
                // "priority": 1200 only orders configs, not individual mixins.
                var annotations = node.invisibleAnnotations;
                if (annotations === null) return node;

                for (var i = 0; i < annotations.size(); i++) {
                    var annotation = annotations.get(i);
                    if (annotation.desc !== 'Lorg/spongepowered/asm/mixin/Mixin;') continue;

                    var values = annotation.values;
                    for (var j = 0; j < values.size(); j += 2) {
                        if (values.get(j) !== 'priority') continue;
                        // Leave an upstream/custom priority alone. Repeated
                        // bytecode requests must not keep changing the value.
                        if (Number(values.get(j + 1)) !== 1000) return node;
                        values.set(j + 1, 1100);
                        ASMAPI.log('INFO', 'ZinZincFix: adjusted Northstar IceBlockMixin priority to 1100');
                        return node;
                    }

                    values.add('priority');
                    values.add(1100);
                    ASMAPI.log('INFO', 'ZinZincFix: adjusted Northstar IceBlockMixin priority to 1100');
                    return node;
                }
                return node;
            }
        }
    };
}
