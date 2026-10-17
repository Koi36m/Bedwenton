# M5 R1 — Binding Clarifications for Codex

**Status:** Accepted *editorial/API-precision clarifications* to the M5 R1 design. This addendum accompanies the independent acceptance review. It changes no mathematical formula, public type/signature, existing test assertion, reference fixture or accepted M1–M4 baseline.

Place a byte-identical copy in `docs/m5/M5-R1-BINDING-ADDENDUM.md` **before writing production M5**.

## C1. `StyleKey.toString()` is required to return the value

In `M5-IMPLEMENTATION-CONTRACT.md` section 2, the general "`toString` may be overridden" allowance does not apply to `StyleKey`.

**Normative:** For every valid `StyleKey key`, `key.toString().equals(key.value())` must be true. This is explicitly required by `SceneValidationTest.styleKeys()`. Implement a `toString()` override; do not rely on Java's generated record form `StyleKey[value=...]`.

## C2. Canonical record constructors enforce all rules

`StyleKey.of(value)` and the exposed `new StyleKey(value)` must enforce exactly the same validation: nonnull value and the required ASCII `namespace:path` pattern. `new Layer(...)` must enforce all nonnull and name-pattern checks. `new SceneLimits(...)` must enforce both numeric ceilings. Do not put validation only in convenience factories.

An optional **additional** adversarial test checking invalid `new StyleKey(value)` is encouraged without modifying the accepted 50 tests.

## C3. Index errors on frame batches

For `FrameBatch.x(i)`, `.y(i)`, `.z(i)` and `.point(i)`, an index `i < 0` or `i >= size()` must throw `IndexOutOfBoundsException`. Valid indices return the saved immutable coordinate triple, with `coordinates()` returning a defensive copy.

## C4. The `PREFIX` count is a single expression

`FrameContractTest.reveal` has stale **display text** showing the older branch/clamp. **Normative M5 R1 contract section 8 is authoritative**:

```java
int k = (int) Math.floor(progress * n);
```

Use it with M2 progress in `[0,1]` and validated `0 <= n <= 2^20`. No extra clamp or `progress>=1` branch should be introduced. Leave the original test assertions unchanged. Test display text can be corrected in a separate documentation/test-metadata-only change after approval, if desired.

## C5. "Validation before allocation" concerns proportional memory

Interpret contract section 3 as follows: reject invalid inputs and counts **before allocating memory proportional to requested shapes, layers or frame output**, to the extent the complete input is available for validation. Small bounded validation temporaries are permitted. During frame evaluation an exception may happen after private intermediate allocations, but `evaluate` must never **return** a partial frame. This preserves the construction and atomicity guarantees without forbidding routine Java validation data structures.

## Non-changes

- All ten R1 public types, chosen values, motion/scene operation order, timing and geometry algorithms remain exactly as designed.
- The 50 handed-off M5 test methods and 1,113 reference rows remain unchanged.
- No Paper/Bukkit classes, runtime emission, gameplay or new dependencies.
- No M1–M4 modifications and no implementation until the owner explicitly authorizes Codex.
- No re-run of Claude's expensive mutation analysis is needed for these clarifications alone. Production verification remains mandatory after implementation.
