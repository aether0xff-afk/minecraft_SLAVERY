# Getting started — milestone 0

**Experimental. Not yet tested on a live server.** Back up the world first, or create a disposable test world.

## Requirements

- Dedicated Fabric server for Minecraft 26.3.
- Java 25, Fabric Loader 0.19.5.
- Fabric API 0.160.5+26.3 and Fabric Language Kotlin required by PuppetPlayers.
- PuppetPlayers 2.1.0+26.3.
- WorkerCore JAR produced by the Gradle build.
- Operator permissions for /worker and /puppet, or use the server console / RCON.

## Build

~~~bash
git clone https://github.com/aether0xff-afk/minecraft_SLAVERY.git
cd minecraft_SLAVERY
gradle :worker-core:build
~~~

The build uses Gradle 9.6.0. There is no committed Gradle wrapper binary yet.
Alternatively, open the latest successful GitHub Actions build, download the
**worker-core-26.3** artifact, extract the JAR, and skip local Gradle installation.
Use the test world first; a successful build is not a runtime compatibility test.

Copy the WorkerCore JAR from worker-core/build/libs to the dedicated server's mods folder.
Do not copy a sources JAR. Install Fabric API, Fabric Language Kotlin, and PuppetPlayers.
Restart the test server.

If the Fabric Loader rejects a dependency, examine the Minecraft and Fabric API versions
in worker-core/build.gradle.kts. Do not silently mix 26.1 and 26.3 jars.

## Smoke test

Stand on flat terrain in a disposable test world. Use an OP account, server console,
or RCON with adequate command permissions.

~~~mcfunction
/worker spawn Worker01
/worker status Worker01
/worker move Worker01 100 64 100
/worker status Worker01
/worker stop Worker01
~~~

Wait for a completion message before issuing another job. Note that PuppetPlayers
joins asynchronously, so spawn may be accepted before /worker status sees the worker.

For a nearby, ordinary stone block whose coordinates you know:

~~~mcfunction
/worker mine Worker01 101 64 100
/worker status Worker01
~~~

The bot **must be in reach**, have an appropriate mining tool, and be positioned with a clear
line of sight. Milestone 0 does not navigate to mining sites, choose tools, or refill inventory.
Successful mining is reported only when the target becomes air.

## Operator and server notes

- /worker commands are restricted to game masters; PuppetPlayers' /puppet normally also needs an OP.
- If the server has no OP account, use its console or configured RCON; do not disable permission checks.
- PuppetPlayers configuration should prohibit untrusted spawning and player puppeteering.
- Unattended work requires correct chunk loading. This milestone does not manage chunk tickets.
- If server pausing on empty is enabled, set pause-when-empty-seconds=0 in server.properties and restart.
- Never enter free-form GPT output into the command bridge; it is allowlisted and must stay that way.

## Current limitations

- Spawn is delegated to PuppetPlayers, not a custom player implementation.
- Movement verifies arrival and detects stalls, but does not mine obstacles.
- Mining only attempts a loaded, reachable block. It may fail under unusual tool, event or mod conditions.
- Stop releases attack/look/move. Other independently scheduled puppet actions are outside this job tracker.
- No persistent jobs, inventory manager, blueprint engine, safety zones, AI service, or training yet.
