import com.google.gson.GsonBuilder;
import org.objectweb.asm.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Conservative bytecode candidate finder; paths do not prove reachability through guards. */
public class ConfigReadAudit {
    static final Map<String, Method> methods = new HashMap<>();
    static final Map<String, String> sources = new TreeMap<>();
    static final List<String> errors = new ArrayList<>();
    static int jars, classes, topLevelJars;
    static class Method {
        String id, jar;
        boolean entry, initializer, direct;
        Set<String> calls = new LinkedHashSet<>(), configFields = new LinkedHashSet<>();
        Set<String> deferredCalls = new HashSet<>();
        Method(String id, String jar) { this.id = id; this.jar = jar; }
    }
    static String id(String owner, String name, String desc) { return owner + "." + name + desc; }
    static void scanJar(InputStream input, String source) throws IOException {
        jars++;
        try (var zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".jar")) {
                    scanJar(new ByteArrayInputStream(zip.readAllBytes()), source + "!/" + name);
                } else if (name.endsWith(".class") && !name.startsWith("META-INF/versions/")) {
                    try { scanClass(zip.readAllBytes(), source); }
                    catch (RuntimeException e) { errors.add(source + "!/" + name + ": " + e); }
                }
            }
        }
    }
    static void scanClass(byte[] bytes, String source) {
        ClassReader cr = new ClassReader(bytes);
        String owner = cr.getClassName();
        classes++;
        sources.putIfAbsent(owner, source);
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                Method m = new Method(id(owner, name, desc), source);
                m.entry = desc.contains("/client/event/ClientTickEvent") || desc.contains("/client/event/RenderFrameEvent")
                        || desc.contains("/client/event/InputEvent") || desc.contains("/client/event/ScreenEvent")
                        || desc.contains("/client/event/RenderGuiEvent");
                m.initializer = name.equals("<clinit>");
                methods.putIfAbsent(m.id, m);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String o, String n, String d, boolean itf) {
                        m.calls.add(id(o, n, d));
                        if (opcode == Opcodes.INVOKESTATIC && !o.equals(owner)) m.calls.add(id(o, "<clinit>", "()V"));
                        if ((o.startsWith("net/neoforged/neoforge/common/ModConfigSpec$")
                                || o.startsWith("net/minecraftforge/common/ForgeConfigSpec$"))
                                && (n.equals("get") || n.equals("getRaw")) && d.startsWith("()")) m.direct = true;
                    }
                    @Override public void visitFieldInsn(int opcode, String o, String n, String d) {
                        if (opcode == Opcodes.GETSTATIC && !o.equals(owner)) m.calls.add(id(o, "<clinit>", "()V"));
                        if (d.contains("ConfigSpec$")) m.configFields.add(o + "." + n);
                    }
                    @Override public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW && !type.equals(owner)) m.calls.add(id(type, "<clinit>", "()V"));
                    }
                    @Override public void visitInvokeDynamicInsn(String n, String d, Handle bootstrap, Object... args) {
                        // Lambda handles are candidates only: constructing a lambda need not invoke it.
                        for (Object arg : args) if (arg instanceof Handle h) {
                            String call = id(h.getOwner(), h.getName(), h.getDesc());
                            m.calls.add(call); m.deferredCalls.add(call);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
    public static void main(String[] args) throws Exception {
        try (var files = Files.list(Path.of(args[0]))) {
            for (Path p : files.filter(p -> p.toString().endsWith(".jar")).sorted().toList()) {
                topLevelJars++;
                scanJar(Files.newInputStream(p), p.getFileName().toString());
            }
        }
        Map<String, List<String>> parents = new HashMap<>();
        for (Method m : methods.values()) for (String call : m.calls)
            if (methods.containsKey(call)) parents.computeIfAbsent(call, k -> new ArrayList<>()).add(m.id);
        Map<String, String> next = new HashMap<>();
        Map<String, Integer> depth = new HashMap<>();
        var queue = new ArrayDeque<String>();
        for (Method m : methods.values()) if (m.direct) { queue.add(m.id); depth.put(m.id, 0); }
        while (!queue.isEmpty()) {
            String callee = queue.remove();
            if (depth.get(callee) >= 8) continue;
            for (String caller : parents.getOrDefault(callee, List.of())) if (!depth.containsKey(caller)) {
                depth.put(caller, depth.get(callee) + 1); next.put(caller, callee); queue.add(caller);
            }
        }
        var candidates = new ArrayList<Map<String, Object>>();
        for (Method m : methods.values().stream().sorted(Comparator.comparing(v -> v.id)).toList()) {
            if ((!m.entry && !m.initializer) || !depth.containsKey(m.id)) continue;
            var path = new ArrayList<String>();
            for (String step = m.id; step != null; step = next.get(step)) path.add(step);
            var row = new LinkedHashMap<String, Object>();
            row.put("jar", m.jar); row.put("kind", m.entry ? "client_event" : "static_initializer");
            row.put("path", path); row.put("configFields", methods.get(path.getLast()).configFields);
            boolean deferred = false;
            for (int i = 0; i < path.size() - 1; i++) deferred |= methods.get(path.get(i)).deferredCalls.contains(path.get(i + 1));
            row.put("deferredLambdaOnPath", deferred);
            candidates.add(row);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("topLevelJars", topLevelJars); result.put("jarsIncludingNested", jars);
        result.put("classFiles", classes); result.put("uniqueClasses", sources.size()); result.put("methods", methods.size());
        result.put("directReadMethods", methods.values().stream().filter(m -> m.direct).count());
        result.put("eventBusStartCallers", methods.values().stream()
                .filter(m -> m.calls.contains("net/neoforged/bus/api/IEventBus.start()V"))
                .map(m -> Map.of("jar", m.jar, "method", m.id)).toList());
        result.put("errors", errors); result.put("candidates", candidates);
        Files.createDirectories(Path.of(args[1]).toAbsolutePath().getParent());
        Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        System.out.println("Scanned " + jars + " jars, " + classes + " classes; " + candidates.size() + " candidates; " + errors.size() + " errors");
    }
}
