# Design-reference cases ported to genuine K0 production tests

The 415 engine and 61 M7 tests are unchanged, byte for byte. All new tests compile the real RingDriver and
DynamicEffectRegistry; neither scratch RingDriverWithHook nor any scratch Aura/Trail/Activation class is used.
The accepted specification, scratch tests and reference files remain preserved in docs/m8/ without changes.

| Design source | Production port | Tests | Adaptation |
|---|---|---:|---|
| SharedCapacityTest | K0SharedCapacityTest | 6 | Same cap/order/replacement assertions, including 120 mixed-start permutations; scripted effect slots |
| M8OnlySchedulingTest | K0M8OnlySchedulingTest | 6 | Same task/plan/zero-request/stale/shutdown assertions; an explicit script pauses/resumes contributions instead of implementing K2 trail history |
| RingDriverWithHookM7CompatTest | K0RingDriverM7CompatTest | 10 | Same ten M7 cases target the real driver using NONE; original M7 tests also run separately |
| TransactionalStartTest | K0TransactionalStartTest | 8 | Same first/replacement failure, factory identity, commit/disposal and refusal assertions; test-only candidates replace visual types |
| AnchorIdentityTest | K0DraftIdentityTest | 10 | Full ordered reason table, nonfinite axes/yaws, swaps, stale/future recovery and bounded termination, world/height/chunk, released/replayed IDs, forged registry and driver drafts, positive controls |
| Additional production safety | K0SafetyTest | 10 | Strict config, disabled routing, failed-start stale callbacks, candidate aliases, disposal-after-commit, reentrancy, update containment, exceptional-close cancellation, thread checks and 1,000 bounded start/replace/stop cycles |

Total new production K0 tests: 50. Existing regression baseline: 476. Expected combined total: 526.
The fake 27-point scene preserves relevant scheduling/accounting assertions from the design tests without shipping
any future visual effect. The reference's lax-AnchorRules probe is replaced by a stronger shared-registry guard:
a malformed snapshot is never passed to the fake effect, even if it would ignore validation. Per-registry rejection
counters and disposed candidates prove the outcomes. The driver forged-draft fixture uses cap 8 to isolate each
bad draft from the valid control, while a separate test proves oversize contributions fail closed at the cap.
No original regression expectation or tolerance is altered. The historical 116 scratch-test / 23-mutant results
are design evidence; they are not counted as K0 production verification. K1 geometry/orientation, K2 history,
K3 activation phases and K4 showcase/soak tests are deferred, not copied into production.
