import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Inventory, not a proof of runtime activation: config plugins and jar-in-jar selection run later. */
public final class MixinAudit {
    static int jars, classes, topLevel;
    static final List<Map<String, Object>> configs = new ArrayList<>(), mixins = new ArrayList<>();
    static final List<String> errors = new ArrayList<>();
    static final Map<String, List<String>> duplicates = new TreeMap<>();
    static final Map<String, Map<String, Object>> unique = new HashMap<>();
    static final Set<String> classNames = new TreeSet<>();
    static final Map<String, Set<String>> observedMixins = new HashMap<>();
    static final Set<String> annotatedMixins = new TreeSet<>(), inventoriedMixins = new TreeSet<>();
    static Object normalize(Object value) {
        if (value instanceof Type t) return t.getClassName();
        if (value instanceof AnnotationNode a) {
            Map<String, Object> out = new LinkedHashMap<>(); out.put("annotation", a.desc);
            if (a.values != null) for (int i = 0; i < a.values.size(); i += 2)
                out.put((String) a.values.get(i), normalize(a.values.get(i + 1)));
            return out;
        }
        if (value instanceof List<?> l) return l.stream().map(MixinAudit::normalize).toList();
        return value;
    }
    static List<AnnotationNode> annotations(List<AnnotationNode> a, List<AnnotationNode> b) {
        List<AnnotationNode> out = new ArrayList<>(); if (a != null) out.addAll(a); if (b != null) out.addAll(b); return out;
    }
    @SuppressWarnings("unchecked")
    static Map<String, Object> values(AnnotationNode a) { return (Map<String, Object>) normalize(a); }
    static void scan(InputStream input, String source) throws Exception {
        jars++;
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (var zip = new ZipInputStream(input)) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null;) {
                if (e.getName().endsWith(".class")) { classes++; classNames.add(e.getName().substring(0, e.getName().length() - 6).replace('/', '.')); }
                if (!e.isDirectory()) entries.put(e.getName(), zip.readAllBytes());
            }
        }
        String metadata = "";
        for (var entry : entries.entrySet()) if (entry.getKey().endsWith(".class") &&
                new String(entry.getValue(), java.nio.charset.StandardCharsets.ISO_8859_1).contains("Lorg/spongepowered/asm/mixin/Mixin;")) {
            ClassNode node = new ClassNode(); new ClassReader(entry.getValue()).accept(node, ClassReader.SKIP_CODE);
            if (annotations(node.visibleAnnotations, node.invisibleAnnotations).stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")))
                annotatedMixins.add(node.name.replace('/', '.'));
        }
        for (String name : List.of("META-INF/neoforge.mods.toml", "META-INF/mods.toml", "META-INF/MANIFEST.MF", "fabric.mod.json"))
            if (entries.containsKey(name)) metadata += new String(entries.get(name), java.nio.charset.StandardCharsets.UTF_8);
        for (var entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".json")) continue;
            JsonObject cfg;
            try {
                JsonElement e = JsonParser.parseString(new String(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8));
                if (!e.isJsonObject()) continue; cfg = e.getAsJsonObject();
                if (!cfg.has("package") || !cfg.get("package").isJsonPrimitive() || !(cfg.has("mixins") || cfg.has("client") || cfg.has("server"))) continue;
            } catch (Exception ignored) { continue; }
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("jar", source); config.put("config", entry.getKey());
            config.put("declaredInMetadata", metadata.contains(entry.getKey()));
            config.put("settings", new Gson().fromJson(cfg, Map.class)); configs.add(config);
            Set<String> listed = new HashSet<>();
            for (String side : List.of("mixins", "client", "server"))
                if (cfg.has(side) && cfg.get(side).isJsonArray()) for (JsonElement n : cfg.getAsJsonArray(side))
                    if (n.isJsonPrimitive()) listed.add(n.getAsString());
            JsonArray observed = new JsonArray();
            for (String n : observedMixins.getOrDefault(entry.getKey(), Set.of()))
                if (!listed.contains(n) && entries.containsKey((cfg.get("package").getAsString() + "." + n).replace('.', '/') + ".class")) observed.add(n);
            cfg.add("observed-plugin", observed);
            for (String side : List.of("mixins", "client", "server", "observed-plugin")) {
                if (!cfg.has(side) || !cfg.get(side).isJsonArray()) continue;
                for (JsonElement name : cfg.getAsJsonArray(side)) {
                    if (!name.isJsonPrimitive()) continue;
                    String className = cfg.get("package").getAsString() + "." + name.getAsString();
                    byte[] bytes = entries.get(className.replace('.', '/') + ".class");
                    if (bytes == null) { errors.add(source + "!" + entry.getKey() + ": missing " + className); continue; }
                    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                    String key = className + ":" + hash + ":" + entry.getKey() + ":" + side;
                    inventoriedMixins.add(className);
                    duplicates.computeIfAbsent(key, k -> new ArrayList<>()).add(source);
                    if (unique.containsKey(key)) continue;
                    ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", key); row.put("jar", source); row.put("config", entry.getKey());
                    row.put("side", side); row.put("mixin", className); row.put("sha256", hash);
                    row.put("declaredInMetadata", metadata.contains(entry.getKey()));
                    row.put("plugin", cfg.has("plugin") && !cfg.get("plugin").isJsonNull() ? cfg.get("plugin").getAsString() : "");
                    row.put("configPriority", cfg.has("priority") ? cfg.get("priority").getAsInt() : 1000);
                    row.put("defaultRequire", cfg.has("injectors") && cfg.getAsJsonObject("injectors").has("defaultRequire")
                            ? cfg.getAsJsonObject("injectors").get("defaultRequire").getAsInt() : 0);
                    for (AnnotationNode a : annotations(node.visibleAnnotations, node.invisibleAnnotations))
                        if (a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) row.put("target", normalize(a));
                    List<Map<String, Object>> methods = new ArrayList<>();
                    for (MethodNode m : node.methods) {
                        List<AnnotationNode> all = annotations(m.visibleAnnotations, m.invisibleAnnotations);
                        List<Map<String, Object>> ann = all.stream().map(MixinAudit::values).toList();
                        if (m.name.startsWith("<")) continue;
                        if (ann.isEmpty() && (m.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0) continue;
                        Map<String, Object> method = new LinkedHashMap<>();
                        method.put("name", m.name); method.put("descriptor", m.desc); method.put("access", m.access);
                        method.put("annotations", ann); methods.add(method);
                    }
                    row.put("methods", methods);
                    if (cfg.has("refmap") && entries.containsKey(cfg.get("refmap").getAsString())) {
                        try {
                            JsonObject rm = JsonParser.parseString(new String(entries.get(cfg.get("refmap").getAsString()), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                            if (rm.has("mappings")) {
                                JsonObject map = rm.getAsJsonObject("mappings");
                                JsonElement mapping = map.get(className.replace('.', '/'));
                                if (mapping == null) mapping = map.get(className);
                                if (mapping != null) row.put("refmap", new Gson().fromJson(mapping, Map.class));
                            }
                        } catch (Exception e) { errors.add(source + ": refmap " + e); }
                    }
                    unique.put(key, row); mixins.add(row);
                }
            }
        }
        for (var entry : entries.entrySet()) if (entry.getKey().endsWith(".jar")) {
            try { scan(new ByteArrayInputStream(entry.getValue()), source + "!/" + entry.getKey()); }
            catch (Exception e) { errors.add(source + "!/" + entry.getKey() + ": " + e); }
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 2 && Files.isRegularFile(Path.of(args[2]))) {
            JsonObject logs = JsonParser.parseString(Files.readString(Path.of(args[2]))).getAsJsonObject();
            for (JsonElement e : logs.getAsJsonArray("applied")) {
                JsonObject a = e.getAsJsonObject();
                observedMixins.computeIfAbsent(a.get("config").getAsString(), k -> new TreeSet<>()).add(a.get("mixin").getAsString());
            }
        }
        try (var files = Files.list(Path.of(args[0]))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".jar")).sorted().toList()) {
                topLevel++; scan(Files.newInputStream(file), file.getFileName().toString());
            }
        }
        for (var row : mixins) row.put("jarOccurrences", duplicates.get(row.get("id")));
        var result = new LinkedHashMap<String, Object>();
        result.put("topLevelJars", topLevel); result.put("jarsIncludingNested", jars); result.put("classFiles", classes);
        result.put("configs", configs); result.put("mixins", mixins); result.put("errors", errors);
        result.put("classNames", classNames);
        result.put("annotatedMixinClasses", annotatedMixins.size());
        annotatedMixins.removeAll(inventoriedMixins);
        result.put("unlistedMixinClasses", annotatedMixins);
        Files.createDirectories(Path.of(args[1]).getParent());
        Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        System.out.println("MIXIN INVENTORY: " + topLevel + " top-level, " + jars + " including nested, " + configs.size()
                + " configs, " + mixins.size() + " distinct configured mixin variants; " + errors.size() + " errors");
    }
}
