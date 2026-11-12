function initializeCoreMod() {
    var ASMAPI = Java.type('net.neoforged.coremod.api.ASMAPI');
    var Opcodes = Java.type('org.objectweb.asm.Opcodes');
    var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
    var FieldInsnNode = Java.type('org.objectweb.asm.tree.FieldInsnNode');
    var AbstractInsnNode = Java.type('org.objectweb.asm.tree.AbstractInsnNode');
    var ArrayList = Java.type('java.util.ArrayList');
    var sceneDesc = 'Lnet/createmod/ponder/foundation/PonderScene;';
    var redirectDesc = 'Lorg/spongepowered/asm/mixin/injection/Redirect;';
    var expressionDesc = 'Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;';

    function annotation(method, desc) {
        var lists = [method.visibleAnnotations, method.invisibleAnnotations];
        for (var l = 0; l < lists.length; l++) {
            if (lists[l] === null) continue;
            for (var i = 0; i < lists[l].size(); i++) {
                if (lists[l].get(i).desc === desc) return lists[l].get(i);
            }
        }
        return null;
    }

    function value(a, key) {
        if (a === null || a.values === null) return null;
        for (var i = 0; i < a.values.size(); i += 2) {
            if (a.values.get(i) === key) return a.values.get(i + 1);
        }
        return null;
    }

    function previousCode(insn) {
        do { insn = insn.getPrevious(); } while (insn !== null && insn.getOpcode() < 0);
        return insn;
    }

    return {
        'ponderer_composable_scale': {
            'target': {'type': 'CLASS', 'name': 'com.nododiiiii.ponderer.mixin.SceneTransformOffsetMixin'},
            'transformer': function (node) {
                for (var i = 0; i < node.methods.size(); i++) {
                    var method = node.methods.get(i);
                    if (method.name !== 'ponderer$effectiveScale') continue;
                    // Already patched, or an upstream implementation with a different signature.
                    if (method.desc !== '(' + sceneDesc + ')F') return node;
                    var redirect = annotation(method, redirectDesc);
                    var at = value(redirect, 'at');
                    if (value(at, 'target') !== sceneDesc + 'scaleFactor:F') return node;
                    var getter = null;
                    var loads = [];
                    var instructions = method.instructions.toArray();
                    for (var j = 0; j < instructions.length; j++) {
                        var insn = instructions[j];
                        if (insn.getOpcode() === Opcodes.ALOAD && insn.var === 1) loads.push(insn);
                        if (insn.getOpcode() === Opcodes.INVOKEINTERFACE &&
                            insn.owner === 'com/nododiiiii/ponderer/mixin/PonderSceneAccessor' &&
                            insn.name === 'ponderer$getScaleFactor' && insn.desc === '()F') {
                            if (getter !== null) return node;
                            getter = insn;
                        }
                    }
                    if (getter === null || loads.length !== 3) return node;
                    var cast = previousCode(getter);
                    var receiver = previousCode(cast);
                    if (cast.getOpcode() !== Opcodes.CHECKCAST || cast.desc !== getter.owner ||
                        receiver.getOpcode() !== Opcodes.ALOAD || receiver.var !== 1) return node;

                    // Consume the original expression result (including Simulated's redirect),
                    // instead of reading the raw field again. Override/zoom/preview code stays intact.
                    method.instructions.set(receiver, new VarInsnNode(Opcodes.FLOAD, 1));
                    method.instructions.remove(cast);
                    method.instructions.remove(getter);
                    for (var k = 0; k < loads.length; k++) {
                        if (loads[k] === receiver) continue;
                        var loadThis = new VarInsnNode(Opcodes.ALOAD, 0);
                        method.instructions.set(loads[k], loadThis);
                        method.instructions.insert(loadThis, new FieldInsnNode(Opcodes.GETFIELD,
                            node.name, 'ponderer$scene', sceneDesc));
                    }
                    method.desc = '(F)F';
                    if (method.localVariables !== null) {
                        for (var v = 0; v < method.localVariables.size(); v++) {
                            var local = method.localVariables.get(v);
                            if (local.index === 1) { local.name = 'originalScale'; local.desc = 'F'; local.signature = null; }
                        }
                    }
                    // Frames refer to the old reference argument. ModLauncher recomputes them.
                    instructions = method.instructions.toArray();
                    for (var f = 0; f < instructions.length; f++) {
                        if (instructions[f].getType() === AbstractInsnNode.FRAME) method.instructions.remove(instructions[f]);
                    }
                    redirect.desc = expressionDesc;
                    // Redirect.at is singular; ModifyExpressionValue.at is an annotation array.
                    for (var a = 0; a < redirect.values.size(); a += 2) {
                        if (redirect.values.get(a) === 'at') {
                            var points = new ArrayList(); points.add(at);
                            redirect.values.set(a + 1, points);
                        }
                    }
                    ASMAPI.log('INFO', 'ZinZincFix: made Ponderer scale compose with Simulated interpolation');
                    return node;
                }
                return node;
            }
        },
        'simulated_picking_scale': {
            'target': {'type': 'CLASS', 'name': 'dev.simulated_team.simulated.mixin.ponder.PonderSceneTransformMixin'},
            'transformer': function (node) {
                for (var i = 0; i < node.methods.size(); i++) {
                    var method = node.methods.get(i);
                    if (method.name !== 'interpolateScaleFactor' || method.desc !== '(' + sceneDesc + 'F)F') continue;
                    var redirect = annotation(method, redirectDesc);
                    if (value(value(redirect, 'at'), 'target') !== sceneDesc + 'scaleFactor:F') continue;
                    var methods = value(redirect, 'method');
                    if (methods === null || methods.contains('screenToScene')) return node;
                    // The inverse transform must use the same animated scale as rendering.
                    methods.add('screenToScene');
                    ASMAPI.log('INFO', 'ZinZincFix: use Simulated animated scale for Ponder mouse picking');
                    return node;
                }
                return node;
            }
        }
    };
}
