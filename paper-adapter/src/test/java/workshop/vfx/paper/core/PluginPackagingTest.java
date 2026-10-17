package workshop.vfx.paper.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
class PluginPackagingTest {
    @TempDir Path temp;
    Path root(){return Path.of(System.getProperty("adapter.root",Path.of(".").toAbsolutePath().toString()));}
    int check(String forbidden, String version) throws Exception {
        Path jar=temp.resolve(UUID.randomUUID()+".jar");
        try(var z=new ZipOutputStream(Files.newOutputStream(jar))) {
            Map<String,String> entries=new HashMap<>(Map.of("plugin.yml","name: BedwentonVfxDemo\nversion: '"+version+"'\n",
                "config.yml","debug: false", "META-INF/bedwenton-build.properties","paperApiVersion=verified",
                "workshop/vfx/delivery/DeliveryPlanner.class","engine", "workshop/vfx/paper/bukkit/VfxDemoPlugin.class","plugin"));
            if(forbidden!=null)entries.put(forbidden,"forbidden");
            for(var e:entries.entrySet()) {z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue().getBytes(StandardCharsets.UTF_8));z.closeEntry();}
        }
        Process process=new ProcessBuilder("python3",root().resolve("tools/verify_plugin_jar.py").toString(),jar.toString(),"0.7.0")
            .redirectErrorStream(true).start();process.getInputStream().readAllBytes();return process.waitFor();
    }
    @Test void syntheticCleanJarPasses() throws Exception {assertEquals(0,check(null,"0.7.0"));}
    @Test void eachServerAndTestDependencyNamespaceIsRejected() throws Exception {
        for(String path:List.of("org/bukkit/Bad.class","io/papermc/Bad.class","net/kyori/Bad.class","com/mojang/Bad.class","org/junit/Bad.class"))
            assertNotEquals(0,check(path,"0.7.0"),path);
    }
    @Test void unresolvedAndWrongPluginVersionsAreRejected() throws Exception {
        assertNotEquals(0,check(null,"${version}"));assertNotEquals(0,check(null,"0.6.0"));
    }
    @Test void sourceBoundaryHasNoPlatformCallsInCoreOrBroadcastAndAsyncInPaper() throws Exception {
        try(var files=Files.walk(root().resolve("src/main/java/workshop/vfx/paper/core"))) {
            for(Path path:files.filter(f->f.toString().endsWith(".java")).toList()) {
                String text=Files.readString(path);
                for(String bad:List.of("org.bukkit","io.papermc","net.kyori","com.mojang"))assertFalse(text.contains(bad),path.toString());
            }
        }
        String paper=Files.readString(root().resolve("src/main/java/workshop/vfx/paper/bukkit/VfxDemoPlugin.java"));
        assertTrue(paper.contains("player.spawnParticle(resolved.particle(),x,y,z,0,0.0,0.0,0.0,0.0,resolved.data(),false)"));
        assertFalse(paper.contains("world.spawnParticle"));assertFalse(paper.contains("runTaskAsynchronously"));
        assertTrue(paper.contains("registerCommand("));assertFalse(paper.contains("getCommand("));
    }
    @Test void failedGradleCannotBeMaskedByStaleSuccessfulXml() throws Exception {
        Path project=temp.resolve("fixture");Path tools=project.resolve("paper-adapter/tools");Files.createDirectories(tools);
        Files.copy(root().resolve("tools/verify.sh"),tools.resolve("verify.sh"));
        Path wrapper=project.resolve("gradlew");Files.writeString(wrapper,"#!/usr/bin/env bash\nexit 23\n");
        assertTrue(wrapper.toFile().setExecutable(true));
        Path reports=project.resolve("build/test-results/test");Files.createDirectories(reports);
        Files.writeString(reports.resolve("TEST-stale.xml"),"<testsuite tests='415' failures='0' errors='0' skipped='0'/>");
        Process process=new ProcessBuilder("bash",tools.resolve("verify.sh").toString(),temp.resolve("evidence").toString())
            .redirectErrorStream(true).start();process.getInputStream().readAllBytes();assertEquals(23,process.waitFor());
    }
}
