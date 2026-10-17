package workshop.vfx.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract sections 1, 2, 3 and 10: dependencies, forbidden calls, integer-only planning, no mutable statics, the exact API. */
class M6BoundaryTest {
    private static final Path SRC = Path.of("src/main/java/workshop/vfx/delivery");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> s = Files.list(SRC)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String code(Path p) throws IOException {
        // strip comments and string literals so documentation may mention forbidden names
        String s = Files.readString(p);
        s = s.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ");
        return s.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
    }

    @Test
    @DisplayName("Imports: only workshop.vfx.scene, M1 core, M2 time and java.util; never Bukkit, Paper, Minecraft, I/O, concurrency or reflection")
    void imports() throws IOException {
        Pattern imports = Pattern.compile("^import\\s+(static\\s+)?([\\w.]+)", Pattern.MULTILINE);
        List<Path> files = sources();
        assertTrue(files.size() >= 10, "sources present: " + files);
        for (Path p : files) {
            String c = code(p);
            Matcher m = imports.matcher(c);
            while (m.find()) {
                String name = m.group(2);
                boolean ok = name.startsWith("workshop.vfx.scene.") || name.startsWith("workshop.vfx.math.time.")
                        || name.startsWith("workshop.vfx.math.core.")
                        || (name.startsWith("java.util.") && !name.startsWith("java.util.concurrent") && !name.startsWith("java.util.function"));
                if (!ok) {
                    fail(p.getFileName() + " imports " + name);
                }
            }
            for (String banned : new String[] {"org.bukkit", "io.papermc", "net.minecraft", "com.destroystokyo", "folia", "java.io.", "java.nio.",
                "java.net.", "java.lang.reflect", "java.util.concurrent"}) {
                assertFalse(c.contains(banned), p.getFileName() + " references " + banned);
            }
        }
    }

    @Test
    @DisplayName("Forbidden: clocks, randomness, threads, locks, logging, System and floating point in planning")
    void forbidden() throws IOException {
        Pattern bad = Pattern.compile("\\b(double|float|Double|Float|Math\\.floor|Math\\.ceil|Math\\.round|StrictMath|Math\\.random|Random"
                + "|System|Thread|Runtime|synchronized|volatile|ThreadLocal|Instant|Clock|LocalDateTime|nanoTime|currentTimeMillis|Logger|printStackTrace"
                + "|hashCode|identityHashCode|parallelStream|Iterator\\s*<\\s*Map)\\b");
        for (Path p : sources()) {
            Matcher m = bad.matcher(code(p));
            if (m.find()) {
                fail(p.getFileName() + " uses " + m.group());
            }
        }
    }

    @Test
    @DisplayName("No mutable static state: every static field is final, of a primitive, String, enum or empty-array constant")
    void statics() throws ClassNotFoundException {
        for (String n : TYPES) {
            for (Field f : Class.forName("workshop.vfx.delivery." + n).getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) {
                    assertTrue(Modifier.isFinal(f.getModifiers()), n + "." + f.getName() + " must be final");
                    Class<?> t = f.getType();
                    assertTrue(t.isPrimitive() || t == String.class || t.isEnum() || t.isArray(), n + "." + f.getName() + " type " + t);
                }
            }
        }
    }

    private static final List<String> TYPES = List.of("QualityTier", "EffectPriority", "LayerRole", "Outcome", "EffectRecipe", "DeliveryRequest",
            "DeliveryPolicy", "DeliveryPlanner", "DeliveryPlan", "EffectDelivery");

    @Test
    @DisplayName("API shape: exactly the ten public types of section 2, with exactly the listed public members")
    void apiShape() throws Exception {
        Set<String> publicTypes = new TreeSet<>();
        for (Path p : sources()) {
            String n = p.getFileName().toString().replace(".java", "");
            Class<?> c = Class.forName("workshop.vfx.delivery." + n);
            if (Modifier.isPublic(c.getModifiers())) {
                publicTypes.add(n);
            }
        }
        assertEquals(new TreeSet<>(TYPES), publicTypes);
        enumConstants(QualityTier.class, "SIMPLE", "BALANCED", "HEAVY");
        enumConstants(EffectPriority.class, "ACTION", "NORMAL", "AMBIENT");
        enumConstants(LayerRole.class, "ESSENTIAL", "STANDARD", "DECORATIVE");
        enumConstants(Outcome.class, "DELIVERED", "THINNED", "SKIPPED_NO_VIEWERS", "SKIPPED_NO_ELIGIBLE_TIER", "SKIPPED_UNSUPPORTED", "SKIPPED_GENERATION_BUDGET",
                "SKIPPED_INACTIVE", "SKIPPED_DELIVERY_BUDGET", "FAILED_EVALUATION");
        members(EffectRecipe.class, "MAX_ROLES", "of(String,EffectPriority,Map,Map)", "name()", "priority()", "domain()", "tiers()",
                "variant(QualityTier)", "role(String)");
        members(DeliveryRequest.class, "MAX_VIEWERS", "of(long,EffectRecipe,QualityTier,int,Pose,Playback,TimePoint,long,long[])", "id()",
                "recipe()", "tier()", "worldId()", "anchor()", "playback()", "now()", "frameOrdinal()", "viewerCount()", "viewer(int)");
        members(DeliveryPlanner.class, "plan(long,List,Set,DeliveryPolicy)");
        members(DeliveryPlan.class, "tickOrdinal()", "deliveries()", "count(Outcome)", "reservedGeneratedPoints()", "sourcePoints()",
                "keptPoints()", "recipients()", "placements()", "unsupportedPoints()");
        members(EffectDelivery.class, "requestId()", "allocationRank()", "outcome()", "tier()", "frame()", "reservedGeneratedPoints()",
                "sourcePoints()", "unsupportedPoints()", "keptPoints()", "placements()", "orderedBatchCount()", "orderedBatch(int)", "kept(int)",
                "selectedIndex(int,int)", "recipientCount()", "recipient(int)", "recipientPoints(int)");
        members(DeliveryPolicy.class, "MAX_REQUESTS_CEILING", "MAX_VIEWER_ENTRIES_CEILING", "MAX_BUDGET", "keptCap(QualityTier)",
                "maxRequests()", "maxViewersPerRequest()", "maxViewerEntries()", "generatedPointBudget()", "ambientGeneratedReserve()",
                "placementBudget()", "ambientPlacementReserve()", "worldPlacementBudget()", "viewerPlacementBudget()", "simpleKeptCap()",
                "balancedKeptCap()", "heavyKeptCap()", "equals(Object)", "hashCode()", "toString()");
        assertTrue(DeliveryPolicy.class.isRecord());
        assertEquals(1, DeliveryPolicy.class.getConstructors().length, "only the canonical constructor");
        for (Class<?> c : List.of(EffectRecipe.class, DeliveryRequest.class, DeliveryPlanner.class, DeliveryPlan.class, EffectDelivery.class)) {
            assertTrue(Modifier.isFinal(c.getModifiers()), c + " final");
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                assertFalse(Modifier.isPublic(k.getModifiers()) || Modifier.isProtected(k.getModifiers()), c + " has no public constructor");
            }
        }
        assertEquals(4096, EffectRecipe.MAX_ROLES);
        assertEquals(1 << 16, DeliveryRequest.MAX_VIEWERS);
        assertEquals(1 << 16, DeliveryPolicy.MAX_REQUESTS_CEILING);
        assertEquals(1L << 24, DeliveryPolicy.MAX_VIEWER_ENTRIES_CEILING);
        assertEquals(1L << 50, DeliveryPolicy.MAX_BUDGET);
    }

    private static void enumConstants(Class<? extends Enum<?>> e, String... names) {
        Object[] values = e.getEnumConstants();
        assertEquals(names.length, values.length, e.getSimpleName());
        for (int i = 0; i < names.length; i++) {
            assertEquals(names[i], ((Enum<?>) values[i]).name());
        }
    }

    private static void members(Class<?> c, String... expected) {
        Set<String> got = new TreeSet<>();
        for (Method m : c.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers()) && !m.isSynthetic()) {
                StringBuilder sb = new StringBuilder(m.getName()).append('(');
                Class<?>[] ps = m.getParameterTypes();
                for (int i = 0; i < ps.length; i++) {
                    sb.append(i > 0 ? "," : "").append(ps[i].getSimpleName());
                }
                got.add(sb.append(')').toString());
            }
        }
        for (Field f : c.getDeclaredFields()) {
            if (Modifier.isPublic(f.getModifiers())) {
                got.add(f.getName());
            }
        }
        assertEquals(new TreeSet<>(List.of(expected)), got, c.getSimpleName());
    }
}
