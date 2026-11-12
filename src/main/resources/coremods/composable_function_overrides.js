function initializeCoreMod() {
    var ASMAPI = Java.type('net.neoforged.coremod.api.ASMAPI');
    var Op = Java.type('org.objectweb.asm.Opcodes');
    var AbstractInsnNode = Java.type('org.objectweb.asm.tree.AbstractInsnNode');
    var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
    var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
    var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
    var ArrayList = Java.type('java.util.ArrayList');
    var callback = 'org/spongepowered/asm/mixin/injection/callback/CallbackInfo';
    var returnCallback = callback + 'Returnable';
    var pickArgs = 'Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/HitResult;' +
        'Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;';
    var pickDesc = '(' + pickArgs + ')Lnet/minecraft/world/item/ItemStack;';

    function methodNamed(node, name, desc) {
        for (var i = 0; i < node.methods.size(); i++) {
            var method = node.methods.get(i);
            if (method.name === name && method.desc === desc) return method;
        }
        return null;
    }

    function previousCode(insn) {
        do { insn = insn.getPrevious(); } while (insn !== null && insn.getOpcode() < 0);
        return insn;
    }

    function dropFrames(method) {
        var instructions = method.instructions.toArray();
        for (var i = 0; i < instructions.length; i++) {
            if (instructions[i].getType() === AbstractInsnNode.FRAME) method.instructions.remove(instructions[i]);
        }
    }

    function inject(method, target, position, cancellable) {
        var annotation = method.visitAnnotation('Lorg/spongepowered/asm/mixin/injection/Inject;', true);
        var methods = annotation.visitArray('method'); methods.visit(null, target); methods.visitEnd();
        var points = annotation.visitArray('at');
        var at = points.visitAnnotation(null, 'Lorg/spongepowered/asm/mixin/injection/At;');
        at.visit('value', position); at.visitEnd(); points.visitEnd();
        annotation.visit('cancellable', cancellable);
        annotation.visit('remap', false);
        annotation.visit('require', 1);
        annotation.visitEnd();
    }

    function inheritedHook(node, name, desc, access, argCount, returnOp) {
        // Mixin cannot inject into an inherited method. Make that inherited behavior
        // explicit without changing it; an upstream concrete override is left intact.
        if (methodNamed(node, name, desc) !== null) return node;
        var method = ASMAPI.getMethodNode();
        method.name = name; method.desc = desc; method.access = access;
        for (var arg = 0; arg <= argCount; arg++) method.instructions.add(new VarInsnNode(Op.ALOAD, arg));
        method.instructions.add(new MethodInsnNode(Op.INVOKESPECIAL, node.superName, name, desc, false));
        method.instructions.add(new InsnNode(returnOp));
        method.maxLocals = argCount + 1; method.maxStack = argCount + 1;
        node.methods.add(method);
        return node;
    }

    return {
        'factory_panel_inherited_pick_hook': {
            'target': {'type': 'CLASS', 'name': 'com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock'},
            'transformer': function (node) {
                return inheritedHook(node, 'getCloneItemStack', pickDesc, Op.ACC_PUBLIC, 5, Op.ARETURN);
            }
        },
        'deployer_composable_pick': {
            'target': {'type': 'CLASS', 'name': 'net.liukrast.deployer.lib.mixin.FactoryPanelBlockMixin'},
            'transformer': function (node) {
                var method = methodNamed(node, 'getCloneItemStack', pickDesc);
                if (method === null) return node;
                var instructions = method.instructions.toArray();
                var fallback = null; var returns = [];
                for (var i = 0; i < instructions.length; i++) {
                    var insn = instructions[i];
                    if (insn.getOpcode() === Op.ARETURN) returns.push(insn);
                    if (insn.getOpcode() === Op.INVOKESPECIAL && insn.name === 'getCloneItemStack' &&
                        insn.desc === pickDesc && insn.owner === 'net/minecraft/world/level/block/Block') {
                        if (fallback !== null) return node;
                        fallback = insn;
                    }
                }
                if (fallback === null || returns.length !== 2 || previousCode(returns[1]) !== fallback) return node;
                var loads = []; var cursor = fallback;
                for (var index = 5; index >= 0; index--) {
                    cursor = previousCode(cursor);
                    if (cursor === null || cursor.getOpcode() !== Op.ALOAD || cursor.var !== index) return node;
                    loads.push(cursor);
                }

                // Reserve slot 6 for CallbackInfoReturnable; preserve every original pattern
                // variable (be, slot, panel) rather than allowing the callback to be overwritten.
                for (var j = 0; j < instructions.length; j++) {
                    var instruction = instructions[j];
                    if ((instruction.getType() === AbstractInsnNode.VAR_INSN ||
                         instruction.getType() === AbstractInsnNode.IINC_INSN) && instruction.var >= 6) instruction.var++;
                }
                if (method.localVariables !== null) {
                    for (var l = 0; l < method.localVariables.size(); l++) {
                        if (method.localVariables.get(l).index >= 6) method.localVariables.get(l).index++;
                    }
                }
                for (var r = 0; r < loads.length; r++) method.instructions.remove(loads[r]);
                method.instructions.remove(fallback);
                method.instructions.set(returns[1], new InsnNode(Op.RETURN));
                // Only an active Deployer panel cancels. Every other case continues through
                // Fluid Logistics (if present), or the original inherited Block hook.
                method.instructions.insertBefore(returns[0], new VarInsnNode(Op.ALOAD, 6));
                method.instructions.insertBefore(returns[0], new InsnNode(Op.SWAP));
                method.instructions.insertBefore(returns[0], new MethodInsnNode(Op.INVOKEVIRTUAL,
                    returnCallback, 'setReturnValue', '(Ljava/lang/Object;)V', false));
                method.instructions.set(returns[0], new InsnNode(Op.RETURN));
                method.name = 'zinzincfix$pickDeployerPanel';
                method.desc = '(' + pickArgs + 'L' + returnCallback + ';)V';
                method.access = Op.ACC_PRIVATE;
                method.signature = null;
                method.invisibleAnnotations = new ArrayList();
                method.maxLocals++; method.maxStack++;
                dropFrames(method);
                inject(method, 'getCloneItemStack' + pickDesc, 'HEAD', true);
                ASMAPI.log('INFO', 'ZinZincFix: compose Deployer custom panel picking with other factory gauges');
                return node;
            }
        },
        'cartography_inherited_tick_hook': {
            'target': {'type': 'CLASS', 'name': 'net.minecraft.client.gui.screens.inventory.CartographyTableScreen'},
            'transformer': function (node) {
                return inheritedHook(node, 'containerTick', '()V', Op.ACC_PROTECTED, 0, Op.RETURN);
            }
        },
        'moonlight_composable_cartography_tick': {
            'target': {'type': 'CLASS', 'name': 'net.mehvahdjukaar.moonlight.core.mixins.CartographyTableScreenMixin'},
            'transformer': function (node) {
                var method = methodNamed(node, 'containerTick', '()V');
                if (method === null) return node;
                var instructions = method.instructions.toArray();
                var superCall = null;
                for (var i = 0; i < instructions.length; i++) {
                    var insn = instructions[i];
                    if (insn.getOpcode() === Op.INVOKESPECIAL && insn.name === 'containerTick' && insn.desc === '()V') {
                        if (superCall !== null) return node;
                        superCall = insn;
                    }
                }
                if (superCall === null || superCall.owner !== node.superName || method.maxLocals !== 1) return node;
                var receiver = previousCode(superCall);
                if (receiver === null || receiver.getOpcode() !== Op.ALOAD || receiver.var !== 0) return node;
                method.instructions.remove(receiver);
                method.instructions.remove(superCall);
                method.name = 'zinzincfix$tickMoonlightCartographyIcons';
                method.desc = '(L' + callback + ';)V';
                method.access = Op.ACC_PRIVATE;
                method.maxLocals = 2;
                dropFrames(method);
                // Supplementaries/the inherited hook already calls super; do not call it twice.
                inject(method, 'containerTick()V', 'TAIL', false);
                ASMAPI.log('INFO', 'ZinZincFix: retain Moonlight cartography icon ticks alongside Supplementaries');
                return node;
            }
        }
    };
}
