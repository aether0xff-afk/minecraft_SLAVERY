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

## Current experimental implementation (Milestone 1 partial)

- /worker spawn forwards a spawn request to PuppetPlayers.
- /worker move uses PuppetPlayer navigation directly, limits distance to 48 blocks, and verifies arrival every server tick.
- /worker mine aims/attacks with player actions; a block raycast must hit the specific target before attack starts.
- /worker place uses the selected BlockItem, aims at adjacent support, uses the item, then verifies the block type and a decrement in inventory count.
- /worker status reports location, current job and structured last job result.
- /worker stop calls PuppetPlayers PlayerActions.reset(), clearing all queued/held actions and navigation.
- Spawn remains an asynchronous PuppetPlayers command. The command being submitted is not proof of a successful join.

PuppetPlayers MoveToAction may finish even when it cannot initiate a path.
WorkerCore instead uses navigation APIs and separately checks actual arrival.
This is compilation-verified, but **world behavior has not yet been tested**.

## Planned interfaces (not implemented)

- Observation: position, dimension, job state, nearby blocks, tools, inventory, hazards.
- GPT: user instruction -> validated goal graph with success predicates and resource constraints.
- JEV: bounded macro-action recommendation, with confidence and decision deadline.
- Validator: no unauthorized commands, region escapes, impossible inventory operations,
  or unbounded resource expenditure.
- Persistence: durable job snapshots and a re-check of world postconditions after restart.

## Known implementation limitations

- Only one transient job per worker; jobs are not persisted across restarts.
- Unloaded terrain, distances above 48 blocks, and block-entity mining are rejected.
- Place targets air only; simple BlockItem placement is supported, but complex blockstate/orientation and inventories are not yet modeled.
- Raycast uses the current player view; dynamic entities could still interfere with held attack/use between ticks.
- There are no allowlisted build regions, automatic hazard responses, autonomous tool selection, container interactions, or blueprint handling yet.

## Safety requirements before unattended operation

- Allowlisted editable regions with protected-area exclusions.
- Block and entity action budgets / rate limits.
- Threat management (lava, void, drops, player damage) and emergency stop.
- Persistent checkpoints, duplicate-operation protections, and audit logs.
- Resource limits for pathfinding, AI inference, chunk tickets and server tick time.
- Explicit failure states and tests against dangerous-world edge cases.
