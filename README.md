# minecraft_SLAVERY — Server-side Minecraft AI Worker

A Fabric **Minecraft Java Edition 26.3** research project for autonomous survival-mode workers that run **inside a dedicated server**, without a separate Minecraft client.

> **Status: initial scaffold / experimental.** This repository does **not** yet contain a trained JEV policy, GPT integration, a complete farming/building bot, or a verified server deployment.

## Architecture

```text
Player command
     |
     v
WorkerCore (Fabric, Java 25) ---> PuppetPlayers 2.1.0+26.3
     |                           fake player, movement, interaction
     +-- deterministic tasks / success checks
     |
     +-- future: structured task/observation protocol
                        |
                        +-- GPT planner (long-horizon plans)
                        +-- JEV policy (local short-horizon decisions)
```

**No external Minecraft client.** The AI service may be a separate local Python process; only PuppetPlayers and WorkerCore interact with the game world.

## First milestone

- `/worker spawn <name>` — delegate puppet spawn to PuppetPlayers.
- `/worker move <name> <x> <y> <z>` — enqueue movement, then **verify actual arrival**.
- `/worker mine <name> <x> <y> <z>` — attempt a nearby block mining operation with actual player actions and verify block removal.
- `/worker status <name>` — view worker position and job status.
- `/worker stop <name>` — cancel tracked work and release inputs.

All commands require game-master/operator permission. Mine works only for already reachable blocks in this milestone. **Do not run on a valuable survival world before testing on a backup.**

## Requirements

- Minecraft Java Edition **26.3**, Fabric Loader **0.19.5**.
- Fabric API **0.160.5+26.3** and PuppetPlayers **2.1.0+26.3**.
- Fabric Language Kotlin (required by PuppetPlayers).
- **JDK 25**, Gradle **9.6.0** (use installed Gradle to bootstrap a wrapper if desired).

The project uses Fabric Loom 1.17 and follows Minecraft's unobfuscated API naming. Dependency versions are pinned in `worker-core/build.gradle.kts`.

## Build

```bash
# After installing JDK 25 and Gradle 9.6:
gradle :worker-core:build

# Optional: create a reusable Gradle wrapper (then commit its scripts/JAR)
gradle wrapper --gradle-version 9.6.0
```

Copy the built WorkerCore JAR to your **test** server's `mods/` folder, alongside the runtime dependencies above. Check `docs/GETTING_STARTED.md` before testing. The repository CI builds the Java module and uploads the compiled test-server mod as the
**worker-core-26.3** artifact on the [Actions page](https://github.com/aether0xff-afk/minecraft_SLAVERY/actions).
Download and unzip that artifact and copy only the runtime JAR to the server's mods folder.
A green CI build verifies compilation, **not** Minecraft server runtime behavior.

## Documentation

- [Architecture and safety](docs/ARCHITECTURE.md)
- [Source-code precedents](docs/PRIOR_RESEARCH.md)
- [Setup and smoke tests](docs/GETTING_STARTED.md)
- [Roadmap](docs/ROADMAP.md)
- [Proposed AI protocol](shared/PROTOCOL.md)

## Constraints

- Survival semantics: no `/setblock`, item spawning, or creative inventory as a substitute for actual work.
- A command being accepted is not the same as the block/goal being accomplished.
- World access stays on the Minecraft server thread; GPT/JEV network calls must not block ticks.
- AI must not be able to issue arbitrary Minecraft server commands.
- Secrets are provided via environment variables and never committed.

## License

**Not yet selected.** We have not adopted or copied code from the referenced projects. Their upstream licenses must be respected before source reuse.
