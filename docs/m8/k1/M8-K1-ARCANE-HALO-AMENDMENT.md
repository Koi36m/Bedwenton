# K1 Arcane Halo visual amendment

Authority: the owner authorization preserved byte-identically in `M8-K1-OWNER-AUTHORIZATION.txt`.
This additive amendment applies to K1 only. Historical R1/R2/R3 documents and their reference code remain unchanged.

The R3 waist layer changes from violet DUST to the already verified M7
`bedwenton:demo_end_rod` (native END_ROD, no payload). The halo changes from gold DUST to the new closed-whitelist
`bedwenton:m8_arcane_halo_cyan`: native DUST, RGB `0x5CE1FF`, size `0.7f`.
The owner selected cyan without an RGB; this implementation uses the existing design's cyan RGB and preserves
the halo's approved size. It does not register or overwrite the historical violet/gold keys or K2/K3 keys.
`ParticleSpec.Dust` and the existing native DustOptions data-type check validate the payload. No payload family is added.

Geometry and timing remain R3: horizontal orbit radius 0.9, feet +1.0, +1 turn/60 ticks; halo radius 0.45,
feet +2.15, -1 turn/80 ticks, both phase zero and LINEAR. The anchor uses the normative StrictMath quaternion
about +Y by negative body yaw. Pitch and head yaw do not orient the rings. No interpolation or retained positions.
SIMPLE is 12+0 points, BALANCED 18+9, HEAVY 24+12. Priority NORMAL, orbit ESSENTIAL, halo STANDARD.
The recipe name remains `bedwenton-m8-aura`; all three variants are constructed once per enabled session.

The new hue does not imply a native END_ROD color override: its appearance is the client's normal END_ROD rendering.
Motion readability and persistence/smear require real-client assessment. Equal-spaced rings have rotational symmetry;
no extra sparkle nodes, layers, point reservations or asymmetric geometry have been added to address that visually.

K1 reuses K0's central identity/skip-streak validation and transactional registry without changing them. AuraEffect
also validates direct contributions defensively; registry validation owns termination/rejection counters. AuraSession
validates a fresh snapshot before attempting a start, preserving a running aura on invalid replacement. World changes
continue on the next valid snapshot; no history or discontinuity-serial implementation is needed for this checkpoint.
K0's reserved serial remains unchanged. Trail, activation, combined showcase and non-player attachment are deferred.

Diagnostics use an injected monotonic timer solely around snapshot reads. `snapshotNanos` excludes validation,
quaternion construction, viewer selection and recipe evaluation. Planner/emitter timings retain their existing meanings.
Snapshot diagnostics are tick-labelled and include start-command reads made in that same tick. They do not drive animation.
