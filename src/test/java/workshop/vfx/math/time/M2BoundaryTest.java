package workshop.vfx.math.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract sections 1 and 6.1: purity, immutability and the dependency boundary of workshop.vfx.math.time. */
class M2BoundaryTest {
    private static final List<Class<?>> TYPES = List.of(TimeDomain.class, TimePoint.class, TickTime.class, Playback.class,
            RepeatMode.class, Timing.class, TimingSample.class, TimingSample.Stage.class);
    private static final List<String> FORBIDDEN_TYPES = List.of("org.bukkit", "io.papermc", "net.minecraft", "org.joml",
            "com.destroystokyo", "net.kyori", "java.time", "java.util.concurrent", "java.util.Random");
    /** Hidden clock reads, randomness, threads and global state are forbidden in the time package's sources. */
    private static final List<String> FORBIDDEN_SOURCE = List.of("System.nanoTime", "System.currentTimeMillis",
            "java.time.", "Instant.now", "java.util.Random", "SplittableRandom", "ThreadLocalRandom", "Math.random",
            "Thread.", "new Thread", "Executor", "synchronized", "volatile", "org.bukkit", "io.papermc",
            "net.minecraft", "org.joml");

    private static void check(Class<?> owner, Class<?> type, String where) {
        Class<?> t = type;
        while (t.isArray()) {
            t = t.getComponentType();
        }
        for (String prefix : FORBIDDEN_TYPES) {
            if (t.getName().startsWith(prefix)) {
                fail(owner.getSimpleName() + " exposes forbidden type " + t.getName() + " in " + where);
            }
        }
    }

    @Test
    @DisplayName("The public API is exactly the eight specified types; none exposes platform, java.time, concurrency or random types (section 1)")
    void apiTypesAndSignatures() throws Exception {
        Set<String> found = new TreeSet<>();
        Path dir = Path.of("src/main/java/workshop/vfx/math/time");
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> found.add(p.getFileName().toString()));
        }
        assertEquals(new TreeSet<>(List.of("Playback.java", "RepeatMode.java", "TickTime.java", "TimeDomain.java",
                "TimePoint.java", "Timing.java", "TimingSample.java")), found, "source files of the time package");
        for (Class<?> c : TYPES) {
            assertTrue(Modifier.isPublic(c.getModifiers()), c + " is public");
            for (Field f : c.getDeclaredFields()) {
                check(c, f.getType(), "field " + f.getName());
            }
            for (Method m : c.getDeclaredMethods()) {
                check(c, m.getReturnType(), "return type of " + m.getName());
                for (Class<?> p : m.getParameterTypes()) {
                    check(c, p, "parameter of " + m.getName());
                }
            }
        }
        assertTrue(TimePoint.class.isRecord() && Playback.class.isRecord() && Timing.class.isRecord() && TimingSample.class.isRecord(),
                "value types are records");
        assertTrue(TimeDomain.class.isEnum() && RepeatMode.class.isEnum() && TimingSample.Stage.class.isEnum(), "enums");
        assertTrue(Modifier.isFinal(TickTime.class.getModifiers()), "TickTime is a final utility class");
    }

    @Test
    @DisplayName("No mutable static state: every static field is final and primitive, String or an enum constant (section 6.1)")
    void noMutableStaticState() {
        for (Class<?> c : TYPES) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    assertTrue(Modifier.isFinal(f.getModifiers()), c.getSimpleName() + "." + f.getName() + " must be final");
                    Class<?> t = f.getType();
                    boolean immutable = t.isPrimitive() || t == String.class || t.isEnum() || f.isEnumConstant()
                            || (t.isArray() && f.isSynthetic()); // the compiler's $VALUES array of an enum
                    assertTrue(immutable, c.getSimpleName() + "." + f.getName() + " must be immutable, is " + t);
                } else {
                    assertTrue(Modifier.isFinal(f.getModifiers()), c.getSimpleName() + "." + f.getName() + " instance fields are final");
                }
            }
        }
    }

    @Test
    @DisplayName("No hidden clock reads, randomness, threads or platform imports in the time package sources (sections 1, 6.1)")
    void noHiddenClocks() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("src/main/java/workshop/vfx/math/time"))) {
            for (Path p : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p);
                for (String token : FORBIDDEN_SOURCE) {
                    if (src.contains(token)) {
                        fail(p.getFileName() + " contains forbidden token '" + token + "'");
                    }
                }
            }
        }
    }
}
