package com.zinzinc.chorefix.mixin.client.alivecombat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.zinzinc.chorefix.optimization.MethodDeclarationCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.xm666.alivecombat.handler.PassHandler", remap = false)
public abstract class PassHandlerMixin {
    @WrapMethod(method = "declaresMethod(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Z")
    private static boolean zinzincfix$cacheDeclaration(
            Class<?> type, Class<?> baseClass, String name, Class<?>[] parameters, Operation<Boolean> original) {
        if (type == null) {
            return original.call(type, baseClass, name, parameters);
        }
        MethodDeclarationCache cache = MethodDeclarationCache.forClass(type);
        Boolean known = cache.find(baseClass, name, parameters);
        if (known != null) {
            return known;
        }
        // Preserve the original superclass search, including its negative result.
        // If the original throws, nothing is cached and the exception propagates.
        boolean declared = original.call(type, baseClass, name, parameters);
        cache.remember(baseClass, name, parameters, declared);
        return declared;
    }
}
