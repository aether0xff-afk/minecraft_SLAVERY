# Architecture — 26.3 server-side worker

## Decided

- No standalone Minecraft client / Mineflayer connection.
- PuppetPlayers 2.1.0+26.3 simulates the player in the Fabric server.
- WorkerCore is the Java 25 server mod that validates and executes allowlisted jobs.
- GPT and JEV are optional higher-level services; not wired in milestone 0.
- Survival-world effects must use ordinary fake-player actions, not /setblock or item grants.
- Game-world access is on the Minecraft server thread; future HTTP inference is asynchronous.

## Layers

1. Commands and permissions: admin requests become typed tasks, never raw AI-supplied commands.
2. Job state machine: accepted / running / success / failed / canceled; one active job per worker.
3. PuppetBridge: maps a restricted action set onto PuppetPlayers.
4. Verification: compares observed state against the desired postcondition.
5. Future planner: GPT turns user instructions into bounded goals/steps.
6. Future policy: JEV decides among permitted macro-actions from structured observations.

## Concrete milestone-0 implementation

- /worker spawn forwards a spawn request to PuppetPlayers.
- /worker move schedules navigation and checks player coordinates every server tick.
- /worker mine aims/attacks with a fake player and checks whether the targeted block became air.
- /worker status reports active tracked work and current location.
- /worker stop cancels tracking and releases movement/attack/look.

PuppetPlayers MoveToAction reports action completion even on an inability to start a path.
Therefore **action completion is not goal completion**. WorkerCore checks actual arrival.

## Planned interfaces (not implemented)

- Observation: position, dimension, job state, nearby blocks, tools, inventory, hazards.
- GPT: user instruction -> validated goal graph with success predicates and resource constraints.
- JEV: bounded macro-action recommendation, with confidence and decision deadline.
- Validator: no unauthorized commands, region escapes, impossible inventory operations,
  or unbounded resource expenditure.
- Persistence: durable job snapshots and a re-check of world postconditions after restart.

## Safety requirements before unattended operation

- Allowlisted editable regions with protected-area exclusions.
- Block and entity action budgets / rate limits.
- Threat management (lava, void, drops, player damage) and emergency stop.
- Persistent checkpoints, duplicate-operation protections, and audit logs.
- Resource limits for pathfinding, AI inference, chunk tickets and server tick time.
- Explicit failure states and tests against dangerous-world edge cases.
