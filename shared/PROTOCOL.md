# Planned WorkerCore <-> AI service contract (not implemented)

The future protocol carries immutable observations from the main server thread
to a local service, and receives **typed macro-action proposals**; it does not
accept arbitrary Minecraft command strings.

## Example observation

~~~json
{
  "schema_version": 0,
  "job_id": "demo-001",
  "worker_id": "Worker01",
  "dimension": "minecraft:overworld",
  "position": [100.5, 64.0, 100.5],
  "goal": {"kind": "harvest_wheat", "region": [96, 64, 96, 112, 68, 112]},
  "hazards": {"lava_nearby": false, "fall_risk": false},
  "inventory": {"minecraft:wheat_seeds": 32},
  "last_action": {"type": "MOVE", "result": "SUCCESS"}
}
~~~

## Example policy response

~~~json
{
  "schema_version": 0,
  "job_id": "demo-001",
  "action": "CONTINUE",
  "confidence": 0.93
}
~~~

Proposed action enum: CONTINUE, MINE, BUILD, REFILL, AVOID_HAZARD,
RECOVER, REQUEST_REPLAN, ABORT_JOB. The original JEV research's internal STOP
meaning must not be conflated with ABORT_JOB.

This is documentation, not a running endpoint. A later API must define deadlines,
sequence IDs, auth, replay protection, action constraints, and error semantics.
