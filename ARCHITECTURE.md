# minecraft-vfx-math: architecture notes (milestone M1)

This file belongs at the root of the new repository. It states the dependency rules and conventions that
every later milestone must preserve.

## Purpose

A small, standalone, pure-Java mathematical foundation for Minecraft visual effects. It computes geometry and
animation state. It never draws anything and never touches Minecraft.

## Dependency rules

1. `workshop.vfx.math.*` production code has **no dependencies**: not Paper, Bukkit, Minecraft, JOML, Mockito,
   or any other library. The JDK only.
2. JOML is allowed in **test scope only**, as an independent reference, and only through
   `src/test/.../JomlOracle.java`.
3. Paper integration, if it happens, is a separate authorized milestone in a separate module
   (`vfx-paper`) that depends on this one. This project never depends on it.
4. The Gradle check `verifyNoRuntimeDependencies` fails the build if the production runtime classpath resolves
   any artifact. `BoundaryTest` additionally checks that core classes do not expose forbidden types.
5. Existing plugins (Full Heal 0.2.0, Astral Prism 0.2.0, ParticleEmitter, others) are never modified from here.
   Their formulas are re-implemented from mathematical descriptions, not copied.
6. No code from Astral Prism's tutorial sources is used until provenance and license are established.

## Layout (M1)

```
workshop.vfx.math.core   Vec3, Quat, Pose, Frame, MathUtil
```
Later milestones add `time`, `curve`, `sample`, `motion`, `graph`, `scene` packages (see the M0 document, Part 3).

## Conventions (summary; authority is CONVENTIONS.md)

- World axes +X east, +Y up, +Z south; right-handed; active rotations by the right-hand rule.
- Positive rotation about +Y is counter-clockwise viewed from above; clockwise viewed from above is a
  positive rotation about -Y.
- Frames are right-handed `(right, up, back)`; `(right, up, forward)` is a reflection and is forbidden.
- Doubles in local space; renderer-specific float conversion only at adapters.
- Numerical policy (Amendment 1, see `IMPLEMENTATION-CONTRACT.md` section 0A): raw arithmetic is plain IEEE-754;
  degeneracy operations (normalize, shortest arc, frame factories) are total for every operand value (a
  caller-supplied fallback is returned unchanged); validated types
  (`Frame`, `Pose`) reject invalid or non-representable states and never clamp.

## Quality gate

A milestone is verified only when `./gradlew clean test` passes on Java 25 and the recorded output is
committed under `verification/`. Until then the source is "written, not verified".
