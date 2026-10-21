package dev.aether0xff.mcworker;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import me.senseiwells.puppet.PuppetPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One-job-per-puppet deterministic executor. All world access happens on server thread.
 * "Action accepted" never means success: completion is based on world postconditions.
 */
final class WorkerJobs {
    private WorkerJobs() {}

    private static final int MOVE_TIMEOUT_TICKS = 1200;
    private static final int MOVE_STALL_TICKS = 200;
    private static final int INTERACT_TIMEOUT_TICKS = 120;
    private static final int MINE_TIMEOUT_TICKS = 600;
    private static final double MAX_MOVE_DISTANCE = 48.0;
    private static final double REACH = 4.5;
    private static final double ARRIVAL_SQ = 2.25;

    private static final Map<UUID, Job> ACTIVE = new LinkedHashMap<>();
    private static final Map<UUID, WorkerResult> LAST = new LinkedHashMap<>();

    private static final class Job {
        final UUID id;
        final String name;
        final WorkerResult.Kind kind;
        final ResourceKey<Level> dimension;
        final Vec3 goal;
        final BlockPos blockPos;
        final BlockState initialBlock;
        final Block expectedBlock;
        final BlockPos support;
        final Direction supportFace;
        final int startTick;
        int lastProgressTick;
        int actionTick = -1;
        double bestDistanceSq;

        Job(PuppetPlayer player, WorkerResult.Kind kind, Vec3 goal,
            BlockPos blockPos, BlockState initialBlock, Block expectedBlock,
            BlockPos support, Direction supportFace, int tick) {
            this.id = player.getUUID();
            this.name = player.getGameProfile().name();
            this.kind = kind;
            this.dimension = player.level().dimension();
            this.goal = goal;
            this.blockPos = blockPos;
            this.initialBlock = initialBlock;
            this.expectedBlock = expectedBlock;
            this.support = support;
            this.supportFace = supportFace;
            this.startTick = tick;
            this.lastProgressTick = tick;
            this.bestDistanceSq = player.position().distanceToSqr(goal);
        }
    }

    static int startMove(CommandSourceStack source, String name, Vec3 goal) {
        PuppetPlayer player = getPuppet(source, name);
        if (player == null || !available(source, player)) return 0;
        if (!Double.isFinite(goal.x) || !Double.isFinite(goal.y) || !Double.isFinite(goal.z)) {
            return WorkerCommands.fail(source, "INVALID_POSITION");
        }
        if (player.position().distanceToSqr(goal) > MAX_MOVE_DISTANCE * MAX_MOVE_DISTANCE) {
            return WorkerCommands.fail(source, "MOVE_LIMIT: max " + (int) MAX_MOVE_DISTANCE + " blocks per command");
        }
        if (!player.level().hasChunkAt(BlockPos.containing(goal))) {
            return WorkerCommands.fail(source, "UNLOADED_CHUNK: worker cannot path into unloaded terrain");
        }
        if (player.position().distanceToSqr(goal) <= ARRIVAL_SQ) {
            LAST.put(player.getUUID(),
                    new WorkerResult(WorkerResult.Kind.MOVE, WorkerResult.State.SUCCEEDED,
                            WorkerResult.Reason.ALREADY_SATISFIED, 0));
            return WorkerCommands.success(source, "ALREADY_SATISFIED");
        }
        if (!PuppetBridge.move(player, goal)) {
            LAST.put(player.getUUID(),
                    new WorkerResult(WorkerResult.Kind.MOVE, WorkerResult.State.FAILED,
                            WorkerResult.Reason.PATH_BLOCKED, 0));
            return WorkerCommands.fail(source, "PATH_BLOCKED: navigation could not start");
        }
        Job job = new Job(player, WorkerResult.Kind.MOVE, goal, null, null, null, null,
                null, source.getServer().getTickCount());
        ACTIVE.put(player.getUUID(), job);
        return WorkerCommands.success(source, "MOVE started; actual arrival will be verified");
    }

    static int startMine(CommandSourceStack source, String name, BlockPos target) {
        PuppetPlayer player = getPuppet(source, name);
        if (player == null || !available(source, player)) return 0;
        if (player.isCreative() || player.isSpectator()) {
            return WorkerCommands.fail(source, "SURVIVAL_REQUIRED");
        }
        ServerLevel level = player.level();
        if (!level.hasChunkAt(target)) {
            return WorkerCommands.fail(source, "UNLOADED_CHUNK");
        }
        BlockState initial = level.getBlockState(target);
        if (initial.isAir()) {
            LAST.put(player.getUUID(),
                    new WorkerResult(WorkerResult.Kind.MINE, WorkerResult.State.SUCCEEDED,
                            WorkerResult.Reason.ALREADY_SATISFIED, 0));
            return WorkerCommands.success(source, "ALREADY_SATISFIED");
        }
        if (!initial.getFluidState().isEmpty() || initial.getDestroySpeed(level, target) < 0
                || level.getBlockEntity(target) != null) {
            return WorkerCommands.fail(source, "UNSAFE_TARGET: cannot mine fluid, unbreakable or block entity");
        }
        Vec3 center = Vec3.atCenterOf(target);
        if (player.getEyePosition().distanceToSqr(center) > REACH * REACH) {
            return WorkerCommands.fail(source, "UNREACHABLE: move closer");
        }
        PuppetBridge.stop(player);
        Job job = new Job(player, WorkerResult.Kind.MINE, center, target.immutable(),
                initial, null, null, null, source.getServer().getTickCount());
        ACTIVE.put(player.getUUID(), job);
        return WorkerCommands.success(source, "MINE started; aiming and block removal are checked");
    }

    static int startPlace(CommandSourceStack source, String name, BlockPos target) {
        PuppetPlayer player = getPuppet(source, name);
        if (player == null || !available(source, player)) return 0;
        if (player.isCreative() || player.isSpectator()) {
            return WorkerCommands.fail(source, "SURVIVAL_REQUIRED");
        }
        ServerLevel level = player.level();
        if (!level.hasChunkAt(target) || !level.getBlockState(target).isAir()) {
            return WorkerCommands.fail(source, "TARGET_NOT_AIR_OR_UNLOADED");
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BlockItem blockItem) || stack.isEmpty()) {
            return WorkerCommands.fail(source, "ITEM_MISSING: hold a placeable block in main hand");
        }
        Block expected = blockItem.getBlock();
        BlockPos support = null;
        Direction face = null;
        Vec3 hit = null;
        // Only place against an existing solid support; no free-floating placement.
        for (Direction candidate : Direction.values()) {
            BlockPos adjacent = target.relative(candidate.getOpposite());
            if (!level.hasChunkAt(adjacent)) continue;
            BlockState base = level.getBlockState(adjacent);
            if (base.isAir() || !base.getFluidState().isEmpty()) continue;
            if (level.getBlockEntity(adjacent) != null) continue; // never open/interact with storage here
            Vec3 point = faceCenter(adjacent, candidate);
            if (player.getEyePosition().distanceToSqr(point) > REACH * REACH) continue;
            support = adjacent.immutable();
            face = candidate;
            hit = point;
            break;
        }
        if (support == null) return WorkerCommands.fail(source, "UNREACHABLE: no nearby support face");
        PuppetBridge.stop(player);
        Job job = new Job(player, WorkerResult.Kind.PLACE, hit, target.immutable(),
                level.getBlockState(target), expected, support, face, source.getServer().getTickCount());
        ACTIVE.put(player.getUUID(), job);
        return WorkerCommands.success(source, "PLACE started; actual block and item consumption will be checked");
    }

    static int status(CommandSourceStack source, String name) {
        PuppetPlayer player = getPuppet(source, name);
        if (player == null) return 0;
        Job active = ACTIVE.get(player.getUUID());
        WorkerResult last = LAST.get(player.getUUID());
        Vec3 p = player.position();
        String current = active == null ? "IDLE" : active.kind + " RUNNING "
                + (source.getServer().getTickCount() - active.startTick) + " ticks";
        return WorkerCommands.success(source, name + " " + current
                + " at " + player.level().dimension() + " "
                + String.format(Locale.ROOT, "(%.1f,%.1f,%.1f)", p.x, p.y, p.z)
                + (last == null ? "" : " | last=" + last.summary()));
    }

    static int stop(CommandSourceStack source, String name) {
        PuppetPlayer player = getPuppet(source, name);
        if (player == null) return 0;
        Job job = ACTIVE.remove(player.getUUID());
        PuppetBridge.stop(player);
        if (job != null) LAST.put(player.getUUID(),
                new WorkerResult(job.kind, WorkerResult.State.CANCELLED,
                        WorkerResult.Reason.STOPPED, source.getServer().getTickCount() - job.startTick));
        return WorkerCommands.success(source, "STOPPED " + name);
    }

    static void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        Iterator<Job> iter = ACTIVE.values().iterator();
        while (iter.hasNext()) {
            Job job = iter.next();
            ServerPlayer raw = server.getPlayerList().getPlayer(job.id);
            WorkerResult.Reason result = null;
            boolean success = false;
            if (!(raw instanceof PuppetPlayer player)) {
                result = WorkerResult.Reason.WORKER_DISCONNECTED;
            } else if (!player.level().dimension().equals(job.dimension)) {
                result = WorkerResult.Reason.DIMENSION_CHANGED;
            } else {
                result = switch (job.kind) {
                    case MOVE -> updateMove(player, job, tick);
                    case MINE -> updateMine(player, job, tick);
                    case PLACE -> updatePlace(player, job, tick);
                };
                success = result == WorkerResult.Reason.ARRIVED
                        || result == WorkerResult.Reason.BLOCK_REMOVED
                        || result == WorkerResult.Reason.BLOCK_PLACED;
            }
            if (result != null) {
                iter.remove();
                if (raw instanceof PuppetPlayer player) PuppetBridge.stop(player);
                WorkerResult finished = new WorkerResult(job.kind,
                        success ? WorkerResult.State.SUCCEEDED : WorkerResult.State.FAILED,
                        result, tick - job.startTick);
                LAST.put(job.id, finished);
                String message = "[worker] " + job.name + ": " + finished.summary();
                server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
                WorkerCoreMod.LOGGER.info(message);
            }
        }
    }

    private static WorkerResult.Reason updateMove(PuppetPlayer player, Job job, int tick) {
        double dist = player.position().distanceToSqr(job.goal);
        if (dist <= ARRIVAL_SQ) return WorkerResult.Reason.ARRIVED;
        if (!player.level().hasChunkAt(BlockPos.containing(job.goal))) {
            return WorkerResult.Reason.UNLOADED_CHUNK;
        }
        if (dist < job.bestDistanceSq - 0.25) {
            job.bestDistanceSq = dist;
            job.lastProgressTick = tick;
        }
        if (tick - job.startTick >= MOVE_TIMEOUT_TICKS) return WorkerResult.Reason.TIMEOUT;
        if (tick - job.lastProgressTick >= MOVE_STALL_TICKS) return WorkerResult.Reason.STALLED;
        return null;
    }

    private static WorkerResult.Reason updateMine(PuppetPlayer player, Job job, int tick) {
        ServerLevel level = player.level();
        if (!level.hasChunkAt(job.blockPos)) return WorkerResult.Reason.UNLOADED_CHUNK;
        BlockState state = level.getBlockState(job.blockPos);
        if (state.isAir()) return WorkerResult.Reason.BLOCK_REMOVED;
        if (!state.equals(job.initialBlock)) return WorkerResult.Reason.TARGET_CHANGED;
        if (player.getEyePosition().distanceToSqr(job.goal) > REACH * REACH) return WorkerResult.Reason.UNREACHABLE;
        if (tick - job.startTick >= MINE_TIMEOUT_TICKS) return WorkerResult.Reason.TIMEOUT;
        PuppetBridge.lookAt(player, job.goal);
        HitResult view = player.pick(REACH, 1.0f, false);
        if (!(view instanceof BlockHitResult hit) || !hit.getBlockPos().equals(job.blockPos)) {
            if (tick - job.startTick > INTERACT_TIMEOUT_TICKS) return WorkerResult.Reason.AIM_BLOCKED;
            // Never hold attack while the raycast points at a different block.
            if (job.actionTick >= 0) PuppetBridge.stop(player);
            job.actionTick = -1;
            return null;
        }
        if (job.actionTick < 0) {
            if (!PuppetBridge.attackHold(player)) return WorkerResult.Reason.ACTION_REJECTED;
            job.actionTick = tick;
        }
        return null;
    }

    private static WorkerResult.Reason updatePlace(PuppetPlayer player, Job job, int tick) {
        ServerLevel level = player.level();
        if (!level.hasChunkAt(job.blockPos) || !level.hasChunkAt(job.support)) {
            return WorkerResult.Reason.UNLOADED_CHUNK;
        }
        BlockState state = level.getBlockState(job.blockPos);
        if (state.is(job.expectedBlock)) return WorkerResult.Reason.BLOCK_PLACED;
        if (!state.isAir()) return WorkerResult.Reason.TARGET_CHANGED;
        if (tick - job.startTick >= INTERACT_TIMEOUT_TICKS) return WorkerResult.Reason.TIMEOUT;
        if (player.getEyePosition().distanceToSqr(job.goal) > REACH * REACH) {
            return WorkerResult.Reason.UNREACHABLE;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BlockItem held) || held.getBlock() != job.expectedBlock || stack.isEmpty()) {
            return WorkerResult.Reason.ITEM_MISSING;
        }
        PuppetBridge.lookAt(player, job.goal);
        HitResult view = player.pick(REACH, 1.0f, false);
        if (!(view instanceof BlockHitResult hit)
                || !hit.getBlockPos().equals(job.support)
                || hit.getDirection() != job.supportFace) {
            return null; // hold off on right click until actual support face is targeted
        }
        if (job.actionTick < 0) {
            if (!PuppetBridge.useOnce(player)) return WorkerResult.Reason.ACTION_REJECTED;
            job.actionTick = tick;
        } else if (tick - job.actionTick > 15) {
            return WorkerResult.Reason.TIMEOUT;
        }
        return null;
    }

    private static Vec3 faceCenter(BlockPos support, Direction outward) {
        return Vec3.atCenterOf(support).add(
                outward.getStepX() * 0.501, outward.getStepY() * 0.501, outward.getStepZ() * 0.501);
    }

    private static boolean available(CommandSourceStack source, PuppetPlayer player) {
        if (ACTIVE.containsKey(player.getUUID())) {
            WorkerCommands.fail(source, "BUSY: use /worker stop before issuing another job");
            return false;
        }
        return true;
    }

    private static PuppetPlayer getPuppet(CommandSourceStack source, String name) {
        if (!WorkerCommands.validName(name)) {
            WorkerCommands.fail(source, "INVALID_NAME");
            return null;
        }
        ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(name);
        if (!(player instanceof PuppetPlayer puppet)) {
            WorkerCommands.fail(source, "NOT_A_PUPPET: " + name);
            return null;
        }
        return puppet;
    }

    static void clear() {
        ACTIVE.clear();
        LAST.clear();
    }
}
