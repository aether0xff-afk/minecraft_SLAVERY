package dev.aether0xff.mcworker;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.resources.Identifier;

final class WorkerCommands {
    private WorkerCommands() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("worker")
                .requires(source -> source.checkPermission(
                        Identifier.withDefaultNamespace("worker"), PermissionLevel.GAMEMASTERS))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> spawn(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("move")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("position", Vec3Argument.vec3())
                                        .executes(ctx -> WorkerJobs.startMove(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                Vec3Argument.getVec3(ctx, "position"))))))
                .then(Commands.literal("mine")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("position", BlockPosArgument.blockPos())
                                        .executes(ctx -> WorkerJobs.startMine(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                BlockPosArgument.getBlockPos(ctx, "position"))))))
                .then(Commands.literal("status")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> WorkerJobs.status(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> WorkerJobs.stop(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"))))));
    }

    private static int spawn(CommandSourceStack source, String name) {
        if (!validName(name)) return fail(source, "Invalid name; use 1-16 letters, numbers or underscores");
        if (source.getServer().getPlayerList().getPlayerByName(name) != null) {
            return fail(source, "Player already online: " + name);
        }
        int accepted = PuppetBridge.spawn(source, name);
        if (accepted <= 0) return fail(source, "Puppet spawn command was rejected");
        return success(source, "Spawn requested for " + name + " (join may complete asynchronously)");
    }

    static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }

    static int success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal("[worker] " + message), false);
        return 1;
    }

    static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal("[worker] " + message));
        return 0;
    }
}
