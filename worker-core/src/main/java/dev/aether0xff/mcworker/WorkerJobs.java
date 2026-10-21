package dev.aether0xff.mcworker;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import me.senseiwells.puppet.PuppetPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Main-server-thread-only milestone jobs.
 * Puppet actions finishing is NOT considered proof that the world goal succeeded.
 */
final class WorkerJobs {
    private WorkerJobs() {}

    private enum Kind { MOVE, MINE }
    private static final Map<UUID, Job> ACTIVE = new LinkedHashMap<>();
    private static final int MOVE_TIMEOUT_TICKS = 1200;
    private static final int MOVE_STALL_TICKS = 200;
    private static final int MINE_TIMEOUT_TICKS = 600;
    private static final double REACH = 4.5;

    private static final class Job {
        final UUID id;
        final String name;
        final Kind kind;
        final Vec3 goal;
        final BlockPos blockPos;
        final BlockState initialBlock;
        final int startTick;
        int lastProgressTick;
        double bestDistanceSq;
        boolean miningStarted;

        Job(ServerPlayer player, Kind kind, Vec3 goal, BlockPos blockPos, BlockState initialBlock,
            int tick) {
            this.id = player.getUUID();
            this.name = player.getGameProfile().name();
            this.kind = kind;
            this.goal = goal;
            this.blockPos = blockPos;
            this.initialBlock = initialBlock;
            this.startTick = tick;
            this.lastProgressTick = tick;
            this.bestDistanceSq = player.position().distanceToSqr(goal);
        }
    }

    static int startMove(CommandSourceStack source, String name, Vec3 goal) {
        ServerPlayer player = getPuppet(source, name);
        if (player == null) return 0;
        if (ACTIVE.containsKey(player.getUUID())) {
            return WorkerCommands.fail(source, "Worker busy; use /worker stop " + name);
        }
        if (!Double.isFinite(goal.x) || !Double.isFinite(goal.y) || !Double.isFinite(goal.z)) {
            return WorkerCommands.fail(source, "Invalid position");
        }
        if (player.position().distanceToSqr(goal) <= 2.25) {
            return WorkerCommands.success(source, name + " already at requested destination");
        }
        MinecraftServer server = source.getServer();
        PuppetBridge.release(server, name);
        if (PuppetBridge.move(server, name, goal) <= 0) {
            return WorkerCommands.fail(source, "PuppetPlayers refused the move command");
        }
        ACTIVE.put(player.getUUID(),
                new Job(player, Kind.MOVE, goal, null, null, server.getTickCount()));
        return WorkerCommands.success(source, "MOVE accepted; arrival will be verified for " + name);
    }

    static int startMine(CommandSourceStack source, String name, BlockPos target) {
        ServerPlayer player = getPuppet(source, name);
        if (player == null) return 0;
        if (ACTIVE.containsKey(player.getUUID())) {
            return WorkerCommands.fail(source, "Worker busy; use /worker stop " + name);
        }
        ServerLevel level = player.level();
        if (!level.hasChunkAt(target)) {
            return WorkerCommands.fail(source, "UNLOADED_CHUNK: target must already be loaded");
        }
        BlockState state = level.getBlockState(target);
        if (state.isAir()) return WorkerCommands.success(source, "Target is already air");
        if (!state.getFluidState().isEmpty() || state.getDestroySpeed(level, target) < 0) {
            return WorkerCommands.fail(source, "UNBREAKABLE_OR_FLUID");
        }
        Vec3 center = Vec3.atCenterOf(target);
        if (player.getEyePosition().distanceToSqr(center) > REACH * REACH) {
            return WorkerCommands.fail(source, "UNREACHABLE: move the worker close to the block first");
        }
        MinecraftServer server = source.getServer();
        PuppetBridge.release(server, name);
        if (PuppetBridge.aimAt(server, name, center) <= 0) {
            return WorkerCommands.fail(source, "Unable to aim at target");
        }
        ACTIVE.put(player.getUUID(),
                new Job(player, Kind.MINE, center, target.immutable(), state, server.getTickCount()));
        return WorkerCommands.success(source, "MINE accepted; removal will be verified for " + name);
    }

    static int status(CommandSourceStack source, String name) {
        ServerPlayer player = getPuppet(source, name);
        if (player == null) return 0;
        Job job = ACTIVE.get(player.getUUID());
        String state = job == null ? "IDLE" : job.kind + " running for "
                + (source.getServer().getTickCount() - job.startTick) + " ticks";
        Vec3 p = player.position();
        return WorkerCommands.success(source, name + " " + state
                + " dimension=" + player.level().dimension().location()
                + " pos=" + String.format(java.util.Locale.ROOT, "(%.1f, %.1f, %.1f)", p.x, p.y, p.z));
    }

    static int stop(CommandSourceStack source, String name) {
        ServerPlayer player = getPuppet(source, name);
        if (player == null) return 0;
        ACTIVE.remove(player.getUUID());
        PuppetBridge.release(source.getServer(), name);
        return WorkerCommands.success(source, "STOPPED " + name);
    }

    static void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        Iterator<Job> iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            Job job = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(job.id);
            String outcome;
            if (!(player instanceof PuppetPlayer)) {
                outcome = "WORKER_DISCONNECTED";
            } else if (job.kind == Kind.MOVE) {
                outcome = updateMove(player, job, tick);
            } else {
                outcome = updateMine(server, player, job, tick);
            }
            if (outcome != null) {
                iterator.remove();
                if (player instanceof PuppetPlayer) PuppetBridge.release(server, job.name);
                String message = "[worker] " + job.name + " " + job.kind + ": " + outcome;
                server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
                WorkerCoreMod.LOGGER.info(message);
            }
        }
    }

    private static String updateMove(ServerPlayer player, Job job, int tick) {
        double distanceSq = player.position().distanceToSqr(job.goal);
        if (distanceSq <= 2.25) return "SUCCESS: ARRIVED";
        if (distanceSq < job.bestDistanceSq - 0.25) {
            job.bestDistanceSq = distanceSq;
            job.lastProgressTick = tick;
        }
        if (tick - job.startTick > MOVE_TIMEOUT_TICKS) return "TIMEOUT: MOVE";
        if (tick - job.lastProgressTick > MOVE_STALL_TICKS) return "PATH_BLOCKED_OR_STALLED";
        return null;
    }

    private static String updateMine(MinecraftServer server, ServerPlayer player, Job job, int tick) {
        ServerLevel level = player.level();
        if (!level.hasChunkAt(job.blockPos)) return "UNLOADED_CHUNK";
        BlockState current = level.getBlockState(job.blockPos);
        if (current.isAir()) return "SUCCESS: BLOCK_REMOVED";
        if (!current.equals(job.initialBlock)) return "TARGET_CHANGED";
        if (player.getEyePosition().distanceToSqr(job.goal) > REACH * REACH) return "UNREACHABLE";
        if (tick - job.startTick > MINE_TIMEOUT_TICKS) return "TIMEOUT: MINE";

        // Give LookAtAction time to orient the fake player before attacking.
        if (!job.miningStarted && tick - job.startTick >= 3) {
            if (PuppetBridge.attackHold(server, job.name) <= 0) return "ATTACK_REJECTED";
            job.miningStarted = true;
        }
        return null;
    }

    private static ServerPlayer getPuppet(CommandSourceStack source, String name) {
        if (!WorkerCommands.validName(name)) {
            WorkerCommands.fail(source, "Invalid name; use 1-16 letters, numbers or underscores");
            return null;
        }
        ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(name);
        if (!(player instanceof PuppetPlayer)) {
            WorkerCommands.fail(source, "Worker is not an online PuppetPlayer: " + name);
            return null;
        }
        return player;
    }

    static void clear() {
        ACTIVE.clear();
    }
}
