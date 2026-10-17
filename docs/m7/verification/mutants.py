"""M7 targeted mutants of the THROWAWAY reference (docs/m7/reference). Each entry: (id, file, exact old text, new text,
description). Applied one at a time by run_mutants.py to a temporary copy; the reference itself is never edited.

E9 (range test written as d2 > r2 instead of !(d2 <= r2)) is deliberately absent: with the finiteness checks in place
d2 can never be NaN (finite inputs overflow only to +infinity), so it is equivalent by construction; the negated form is
kept as defence in depth (see M7-R3-VERIFICATION.md)."""
M = [
("E1","DefaultPlanEmitter.java","        ordered.sort(Comparator.comparingInt(EffectDelivery::allocationRank));\n","","R1 F2 defect: emit in plan (input) order"),
("E2","OwnerAwareRecipientRule.java","boolean allowed = viewerId == c.ownerViewerId() ? f.hasUsePermission() : f.hasViewPermission();","boolean allowed = f.hasUsePermission() || f.hasViewPermission();","R1 F1 defect: owner-blind rule (use OR view)"),
("E3","DefaultPlanEmitter.java","            SkipReason bad = c == null ? SkipReason.MISSING_REQUEST_CONTEXT","            if (c == null) c = new RequestContext(d.requestId(), d.recipient(0), 0, 0.5, 64.1, 0.5);\n            SkipReason bad = c == null ? SkipReason.MISSING_REQUEST_CONTEXT","missing context replaced by a guessed owner (R2 mutant, pattern updated for the R3 code)"),
("E4","SessionStyleHealth.java","@Override public void recordSuccess(StyleKey style) { streaks.put(style, 0); }","@Override public void recordSuccess(StyleKey style) { streaks.clear(); }","any style's success resets every streak"),
("E5","DefaultPlanEmitter.java","        Set<StyleKey> disabledNow = new LinkedHashSet<>();\n","        Set<StyleKey> disabledNow = new LinkedHashSet<>();\n        for (StyleKey k : java.util.List.of(workshop.vfx.scene.StyleKey.of(\"bedwenton:demo_dust_red\"), workshop.vfx.scene.StyleKey.of(\"bedwenton:demo_end_rod\"))) if (!health.isDisabled(k)) health.recordSuccess(k);\n","R1 wording: streak reset at each tick"),
("E6","OwnerFirstViewerSelection.java","        if (ownerEligible && cap > 0) out[n++] = owner;\n","","owner not admitted before capping"),
("E7","OwnerAwareRecipientRule.java","        if (!finite(f.x(), f.y(), f.z())) return Optional.of(SkipReason.VIEWER_POSITION_INVALID);\n","","R3: viewer position not validated (the reviewed NaN fail-open)"),
("E8","OwnerAwareRecipientRule.java","        if (!finite(c.anchorX(), c.anchorY(), c.anchorZ())) return Optional.of(SkipReason.INVALID_REQUEST_CONTEXT);\n","","R3: anchor not validated in the rule"),
("E10","OwnerAwareRecipientRule.java","if (!Double.isFinite(radius) || radius < 0) {","if (false) {","R3: view radius not validated"),
("E11","DefaultPlanEmitter.java","                    : c.requestId() != d.requestId() ? SkipReason.MISMATCHED_REQUEST_CONTEXT\n","","R3: mismatched context accepted"),
("E12","DefaultPlanEmitter.java","                    : !validContext(c) ? SkipReason.INVALID_REQUEST_CONTEXT : null;","                    : null;","R3: invalid context not rejected at delivery level"),
("E13","DefaultPlanEmitter.java","inSkippedDeliveries += d.placements();","","R3: skipped-delivery placements not accounted"),
("E14","DefaultPlanEmitter.java","inSkippedRecipients += d.recipientPoints(r);","","R3: skipped-recipient placements not accounted"),
]
