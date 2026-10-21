package dev.aether0xff.mcworker;

/** Structured local result; presentation text is deliberately not used for decisions. */
record WorkerResult(Kind kind, State state, Reason reason, int ticks) {
    enum Kind { MOVE, MINE, PLACE }
    enum State { SUCCEEDED, FAILED, CANCELLED }
    enum Reason {
        ARRIVED, BLOCK_REMOVED, BLOCK_PLACED, ALREADY_SATISFIED,
        STOPPED, WORKER_DISCONNECTED, DIMENSION_CHANGED, UNLOADED_CHUNK,
        PATH_BLOCKED, STALLED, TIMEOUT, UNREACHABLE, AIM_BLOCKED,
        TARGET_CHANGED, TOOL_MISSING, ITEM_MISSING, ACTION_REJECTED
    }

    String summary() {
        return kind + " " + state + " " + reason + " after " + ticks + " ticks";
    }
}
