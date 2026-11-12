import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Select original methods, never rewrite the bytecode of the behavior under test. */
public class PrepareFixtures {
    static ZipFile mod(Path mods, String prefix) throws Exception {
        try (var paths = Files.list(mods)) {
            var candidates = paths.filter(p -> p.getFileName().toString().startsWith(prefix) && p.toString().endsWith(".jar")).toList();
            if (candidates.size() != 1) throw new IllegalStateException("Expected one " + prefix);
            return new ZipFile(candidates.getFirst().toFile());
        }
    }
    static void copy(ZipFile from, ZipOutputStream to, String name, Set<String> methods) throws Exception {
        String entry = name.replace('.', '/') + ".class";
        byte[] bytes = from.getInputStream(Objects.requireNonNull(from.getEntry(entry), entry)).readAllBytes();
        if (methods != null) {
            ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
            node.methods.removeIf(m -> !methods.contains(m.name));
            ClassWriter writer = new ClassWriter(0); node.accept(writer); bytes = writer.toByteArray();
        }
        to.putNextEntry(new ZipEntry(entry)); to.write(bytes); to.closeEntry();
    }
    static void config(Path path, ZipFile original, String list, String side) throws Exception {
        var entry = original.getEntry(path.getFileName().toString());
        var config = JsonParser.parseString(new String(original.getInputStream(entry).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        // Retain original overwrite/injector policies. Only unrelated mixins and
        // plugins requiring the entire mod are excluded from these narrow fixtures.
        for (String key : List.of("mixins", "client", "server", "plugin", "refmap")) config.remove(key);
        config.add(side, JsonParser.parseString("[" + list + "]"));
        Files.writeString(path, config.toString());
    }
    public static void main(String[] args) throws Exception {
        Path mods = Path.of(args[0]), out = Path.of(args[1]), resources = out.resolve("resources");
        Files.createDirectories(resources);
        boolean left = Set.of("both", "baseline", "left").contains(args[2]);
        boolean right = Set.of("both", "baseline", "right").contains(args[2]);
        try (ZipOutputStream jar = new ZipOutputStream(Files.newOutputStream(out.resolve("fixture.jar")));
             ZipFile deployer = mod(mods, "deployer-"); ZipFile fluid = mod(mods, "fluidlogistics-");
             ZipFile moonlight = mod(mods, "moonlight-"); ZipFile supplementaries = mod(mods, "supplementaries-")) {
            jar.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            jar.write("Manifest-Version: 1.0\nFMLModType: GAMELIBRARY\nAutomatic-Module-Name: override.smoke.fixtures\n\n".getBytes()); jar.closeEntry();
            if (left) {
                copy(deployer, jar, "net.liukrast.deployer.lib.mixin.FactoryPanelBlockMixin", Set.of("<init>", "getCloneItemStack", "getTargetedSlot"));
                copy(moonlight, jar, "net.mehvahdjukaar.moonlight.core.mixins.CartographyTableScreenMixin", null);
            }
            if (right) {
                copy(fluid, jar, "com.yision.fluidlogistics.mixin.logistics.FactoryGaugeHostBlockMixin", Set.of("<init>", "getCloneItemStack"));
                copy(supplementaries, jar, "net.mehvahdjukaar.supplementaries.mixins.CartographyTableScreenMixin", Set.of("<init>", "containerTick", "supp$animateSlots"));
            }
            // Helpers retain the original API behavior used in the assertions, including fallback
            // to a normal gauge for non-resource panels and registration of custom slot icons.
            copy(fluid, jar, "com.yision.fluidlogistics.content.logistics.factoryGauge.FactoryGaugeHostHooks", Set.of("pickStackFor"));
            copy(fluid, jar, "com.yision.fluidlogistics.content.logistics.factoryGauge.FactoryGaugeDropPolicy", null);
            copy(moonlight, jar, "net.mehvahdjukaar.moonlight.api.util.CartographyTableHelper", null);
            copy(moonlight, jar, "net.mehvahdjukaar.moonlight.api.util.CartographyTableHelper$SlotExtras", null);
            config(resources.resolve("deployer.mixins.json"), deployer, left ? "\"FactoryPanelBlockMixin\"" : "", "mixins");
            config(resources.resolve("fluidlogistics.mixins.json"), fluid, right ? "\"logistics.FactoryGaugeHostBlockMixin\"" : "", "mixins");
            config(resources.resolve("moonlight-common.mixins.json"), moonlight, left ? "\"CartographyTableScreenMixin\"" : "", "client");
            config(resources.resolve("supplementaries-common.mixins.json"), supplementaries, right ? "\"CartographyTableScreenMixin\"" : "", "client");
        }
        System.out.println("Prepared original override fixtures: " + args[2]);
    }
}
