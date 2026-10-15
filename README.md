# minecraft-vfx-math

Standalone Java 25 mathematical core, milestone M1. Production has no dependencies.
Implements Vec3, Quat, Frame, Pose, and MathUtil under workshop.vfx.math.core.

Set JAVA_HOME to a Java 25 JDK and run `./gradlew clean test` and `./gradlew check`.
Gradle 9.1.0 is pinned by the wrapper. Tests use JUnit Jupiter 5.12.2 and JOML 1.10.9
(MIT license, test scope only). Contract and conventions are in docs/.

This is the first implementation pass against approved handoff revision 3.
Final M1 acceptance requires Claude's independent review and adversarial tests.
