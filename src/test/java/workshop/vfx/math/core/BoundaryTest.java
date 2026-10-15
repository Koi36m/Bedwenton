package workshop.vfx.math.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture boundary: the production core must not expose or depend on Paper, Bukkit, Minecraft or JOML types.
 * (JOML is allowed on the TEST classpath only. The Gradle task verifyNoRuntimeDependencies enforces the
 * production classpath separately.)
 */
class BoundaryTest {
    private static final List<String> FORBIDDEN = List.of("org.bukkit", "io.papermc", "net.minecraft", "org.joml", "com.destroystokyo", "net.kyori");
    private static final List<Class<?>> CORE = List.of(Vec3.class, Quat.class, Pose.class, Frame.class, MathUtil.class);

    private static void check(Class<?> owner, Class<?> type, String where) {
        Class<?> t = type;
        while (t.isArray()) {
            t = t.getComponentType();
        }
        String name = t.getName();
        for (String prefix : FORBIDDEN) {
            if (name.startsWith(prefix)) {
                fail(owner.getSimpleName() + " exposes forbidden type " + name + " in " + where);
            }
        }
    }

    @Test
    @DisplayName("Core classes never expose Paper, Bukkit, Minecraft or JOML types")
    void coreExposesNoPlatformTypes() {
        for (Class<?> c : CORE) {
            check(c, c.getSuperclass() == null ? Object.class : c.getSuperclass(), "superclass");
            for (Class<?> i : c.getInterfaces()) {
                check(c, i, "interface");
            }
            for (Field f : c.getDeclaredFields()) {
                check(c, f.getType(), "field " + f.getName());
            }
            for (Method m : c.getDeclaredMethods()) {
                check(c, m.getReturnType(), "return type of " + m.getName());
                for (Class<?> p : m.getParameterTypes()) {
                    check(c, p, "parameter of " + m.getName());
                }
            }
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                for (Class<?> p : k.getParameterTypes()) {
                    check(c, p, "constructor parameter");
                }
            }
        }
    }

    @Test
    @DisplayName("Paper and Bukkit are not on the test classpath either")
    void platformApisAreAbsent() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.bukkit.Bukkit"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("io.papermc.paper.ServerBuildInfo"));
    }
}
