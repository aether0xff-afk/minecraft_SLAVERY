# Source-code precedents

Reviewed starting points (source inspection, not integration or build verification):

## PuppetPlayers — actual 26.3 execution foundation

https://github.com/senseiwells/PuppetPlayers/tree/26.3

- PuppetPlayer extends Arcade FakePlayer.
- PlayerActions manages the server-side interaction and action queue.
- MoveToAction uses navigation and may finish when it cannot initiate a path.
- PlayerActionProvider allows additional actions.
- Selected project dependency: me.senseiwells:puppet-players:2.1.0+26.3.

## AIBot — task and goal design

https://github.com/zoyluoblue/mc_aiplayer

- Minecraft 1.21.3, Java, older Yarn mapping: **not directly copyable to 26.3**.
- Task.java, GoalPredicate.java, TaskManager.java: separate action lifecycle and goals.
- BlockMiner.java: start mining once, observe completion, avoid resetting progress.
- FarmTask.java / BuildTask.java: explicit phases and timeouts.
- AtomicSnapshotFile.java: crash-conscious snapshot writes.

## AnimaFabric — planner and async dispatch

https://github.com/MapleAries/AnimaFabric

- Minecraft 26.1.2, Kotlin, Carpet-based and native drivers.
- TaskPlanner.kt: natural language -> small step plans, file-based status and recovery.
- GameThreadDispatcher.kt: hand control to the Minecraft thread.
- ActionDriver.kt: execution backend abstraction.
- ActionResultClassifier.kt classifies strings; WorkerCore should use structured results.
- Some craft-related paths include creative item grants; these must not be carried into survival automation.

## Reuse policy

Use source as a reference for design until per-project licensing and API compatibility
are reviewed. Do not vendor third-party source code without license compliance or
claim 26.3 runtime compatibility merely from a matching Maven version.
