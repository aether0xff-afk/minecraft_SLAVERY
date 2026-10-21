# Roadmap

## Milestone 0 — committed scaffolding (experimental)

- [x] Fabric 26.3 multi-module Gradle project and pinned dependency versions.
- [x] PuppetPlayers bridge and permission-protected worker commands.
- [x] Minimal MOVE/MINE task lifecycle and postcondition verification.
- [x] Automated build workflow.
- [x] GitHub Actions compile success on JDK 25 / Gradle 9.6.0 (2026-10-21).
- [ ] Downloadable GitHub Actions artifact verified after workflow update.
- [ ] Disposable-server smoke tests.
- [ ] Fix any version-specific build or runtime issues found by CI.

## Milestone 1 — deterministic worker

- Safe position/path management with path failure diagnostics.
- Mine, place, harvest and interact primitives; inventory-safe tool selection.
- Success/failure enums and tests, rather than display strings.
- Permission/region boundaries, resource budgets, and emergency-stop guarantees.

## Milestone 2 — useful survival work

- Automated wheat farming, harvest/replant, crop maturity detection.
- Container deposits, resupply, item accounting, tool durability.
- Chunk loading policy, restart checkpoints and idempotent job recovery.

## Milestone 3 — construction

- Blueprint parsing and full BlockState validation.
- Dig/stand/place plans and material counting.
- Nether highway templates; lava and dimension safety.
- Villager logistics and trading after construction/transport are reliable.

## Milestone 4 — agent layer

- Structured GPT planner with typed tool schemas and explicit budgets.
- Local JEV service: macro-action policy, not unbounded low-level clicks.
- Baseline policy and training/evaluation environment.
- Compare task success, resource waste, TPS impact, completion time, recovery rates.

## Definition of done for a useful autonomous release

A job may only be reported complete after the actual world and/or inventory
postconditions are satisfied. It must be cancelable, auditable, recoverable after
a server restart, and operate without a connected Minecraft client.
