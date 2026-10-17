# M6 risks and open questions (R1 proposal)

## Risks

| # | Risk | Likelihood / impact | Mitigation in R1 | Residual |
|---|---|---|---|---|
| R1 | Budgets are mistaken for safe server limits | medium / high | No defaults and no recommended numbers (DM6-11). The docs say placements are not packets. | Owners must measure on their servers through the adapter. |
| R2 | The conservative generation reservation (`maxPointsPerFrame`) over-reserves for staged scenes (healer: 3 pulses reserved, 1 visible) | high / medium | `sourcePoints` reports actual work. Authors can split stages into separate recipes. | Admission may reject effects whose visible cost would fit; see R4. |
| R3 | NORMAL effects starve under sustained ACTION demand | medium / medium | Stated honestly (DM6-5) and tested (`FairnessTest.strictPriority`). | Q1: a NORMAL reserve or a carried deficit (both need state or more policy fields). |
| R4 | No per-layer activity preflight | medium / low | Evaluation stays exact. The open option is a per-layer active-window bound derived from M2 timing. | It would duplicate M5 visibility logic; this was rejected for R1. |
| R5 | Strided phase rotation reads as shimmer at heavy thinning | medium / low | Coverage is guaranteed over g ordinals. A constant ordinal gives a static selection. | It needs visual review on real clients with the adapter. |
| R6 | Budgets of independent calls are not coordinated (per-world threads, Folia regions) | medium / high for multi-threaded hosts | No ledger (DM6-10). The host must split budgets explicitly. | This is adapter design. Folia is not claimed. |
| R7 | Outcome `DELIVERED` is misread as "particles were shown" | low / medium | `DELIVERED` means fully planned for every viewer. Emission results are adapter metrics. | Documentation only. |
| R8 | Large inputs allocate before rejection | low / medium | Sizes are checked before copies. A hostile `size()` is rejected unread. A measured denied preflight allocates under 64 KiB. | The caller still builds its request objects (bounded by its own memory). |
| R9 | The tests encode the throwaway reference's interpretation | medium / medium | An independent Python oracle written from the contract; an independent invariant checker; 58 mutants. | The reviewer should check the contract text against both oracles, not against the reference. |
| R10 | Planner cost at the ceilings (2^16 requests, 2^24 viewer entries) | low / medium | Measured with the reference (`performance.txt`). Complexity is stated in the contract. | The reference boxes budgets in hash maps: about 54 ms per plan at 2^16 requests and 60 MB at 2^20 viewer entries. Production should use primitive arrays and sort the remainders. |
| R11 | Provenance confusion between `d4970cc` (accepted GitHub snapshot) and `d5a8868` (unpublished local commit) | low / medium | Every M6 document names `d4970cc` as the baseline. | None. |
| R12 | Nothing published to GitHub from this environment (pushes refused, 403) | high / low | Recoverable bundle and ZIP with SHA-256 checksums. | The owner publishes, then runs CI. |

## Open questions for review

- **Q1.** Should NORMAL get its own reserve, which would add two policy fields, or should the AMBIENT-only reserve
  remain?
- **Q2.** Should a recipe be able to override `keptCap` per tier? R1 says no: quotas stay in policy, and geometry stays
  in the recipe.
- **Q3.** Is `maxPointsPerFrame` the right conservative cost, or should M6 accept an author-declared visible bound,
  validated by M5 at evaluation time?
- **Q4.** Should `DELIVERED` remain strict (every viewer gets every point), or should a separate "saturated viewers"
  counter be added and `DELIVERED` relaxed?
- **Q5.** Should the frame batches whose style is unsupported still count against the generation budget? R1 says yes,
  because M5 generates them. The alternative is per-style scenes, which is an authoring burden.
