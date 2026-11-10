package com.zinzinc.chorefix.optimization;

import java.util.Arrays;
import java.util.Objects;

/** Small, class-lifetime caches for AliveCombat's handful of interaction signatures. */
public final class MethodDeclarationCache {
    private static final ClassValue<MethodDeclarationCache> BY_CLASS = new ClassValue<>() {
        @Override
        protected MethodDeclarationCache computeValue(Class<?> type) {
            return new MethodDeclarationCache();
        }
    };

    private volatile Entry[] entries = new Entry[0];

    private MethodDeclarationCache() {
    }

    public static MethodDeclarationCache forClass(Class<?> type) {
        return BY_CLASS.get(type);
    }

    public Boolean find(Class<?> baseClass, String name, Class<?>[] parameters) {
        // No query key, array copy or exception is allocated on a cache hit.
        for (Entry entry : entries) {
            if (entry.matches(baseClass, name, parameters)) {
                return entry.declared;
            }
        }
        return null;
    }

    public synchronized void remember(Class<?> baseClass, String name, Class<?>[] parameters, boolean declared) {
        if (find(baseClass, name, parameters) != null) {
            return;
        }
        Entry[] updated = Arrays.copyOf(entries, entries.length + 1);
        updated[entries.length] = new Entry(baseClass, name, parameters == null ? null : parameters.clone(), declared);
        entries = updated;
    }

    private record Entry(Class<?> baseClass, String name, Class<?>[] parameters, Boolean declared) {
        private boolean matches(Class<?> base, String method, Class<?>[] types) {
            return baseClass == base && Objects.equals(name, method) && Arrays.equals(parameters, types);
        }
    }
}
