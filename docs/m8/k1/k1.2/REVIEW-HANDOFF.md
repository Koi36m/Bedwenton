# K1.2 independent implementation review handoff

READY FOR INDEPENDENT K1.2 IMPLEMENTATION REVIEW. Owner/independent acceptance remains pending.
Read exact owner authorization and inline drawing context, ../M8-K1.2-VISUAL-CORRECTION.md, implementation report,
SIGIL-GEOMETRY-PREVIEW.svg and focused smoke checklist. Preview is sampled geometry, not native Minecraft evidence.

Native production 22e6926af55e928fea4f3894b85ff5c23a034e63 descends from exact K1.1 evidence d5a1fa2b59662af98d228f1f63fc6e87af5e9105.
GitHub snapshot 0b95b61299243b119adc4147b3f3a0dccbfb4db4 has actual snapshot ancestry; complete tested trees match
1ad4e8c25aa2de697b53119f6ceff0f44363cd9e. Final evidence commit/tree is in external GIT-CHECKPOINT.json.
No source/build/test change follows the successful tested checkpoint. Native bundle preserves original ancestry and refs.

Production Java diff is only AuraRecipe. Stable vertical XY outline r0.78, center(0,1.25,-0.65), asymmetric cubic strokes
spin +Z in 80 ticks. Halo coordinates/payload/timing are unchanged, with 84 captured K1.1 coordinates verified bit-for-bit.
Tier allocations 20/34/44 fit existing caps. Only version and six visual-test files change otherwise; exact diffs supplied.
All 589 prior executed test IDs remain in 603 passing cases. Both checks, pinned Paper/JAR/runtime/compiler gates and
seven byte-identical reference regenerations passed freshly. No numerical tolerances loosened or original data changed.

Suggested adversarial review: parent/child motion order and player-relative rear offset, stable disc normal under yaw,
curve convex-hull containment, asymmetry/half-turn observability, wrap continuity, junction duplication, unchanged halo,
M6 role ordering under thinning and retained permission/lifecycle/transaction behavior. No engine changes are needed.
Assess actual appearance/persistence/rotation readability on disposable Paper only. All K1.2 real-server observations are
NOT RUN; two-player permission isolation remains required before outside showcase. No TPS/MSPT or native performance
claim follows from synthetic timings. Do not merge, release, deploy live, self-accept or begin K2–K4.
