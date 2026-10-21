package dev.aether0xff.mcworker;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

/**
 * Allowlisted PuppetPlayers command bridge. NEVER send raw GPT/JEV strings here.
 * Commands run on the server thread. Usernames are validated by WorkerCommands.
 */
final class PuppetBridge {
    private PuppetBridge() {}

    static int spawn(CommandSourceStack source, String name) {
        return issue(source, "puppet " + name + " spawn");
    }

    static int move(MinecraftServer server, String name, Vec3 pos) {
        return issue(server, "puppet " + name
                + " actions run minecraft:move_to position "
                + pos.x + " " + pos.y + " " + pos.z);
    }

    static int aimAt(MinecraftServer server, String name, Vec3 pos) {
        return issue(server, "puppet " + name
                + " actions run minecraft:look_at position "
                + pos.x + " " + pos.y + " " + pos.z + " true");
    }

    static int attackHold(MinecraftServer server, String name) {
        return issue(server, "puppet " + name + " actions run minecraft:attack hold");
    }

    static void release(MinecraftServer server, String name) {
        issue(server, "puppet " + name + " actions run minecraft:attack release");
        issue(server, "puppet " + name + " actions run minecraft:interrupt_look_at");
        issue(server, "puppet " + name + " actions run minecraft:interrupt_move_to");
    }

    private static int issue(MinecraftServer server, String command) {
        return issue(server.createCommandSourceStack(), command);
    }

    private static int issue(CommandSourceStack source, String command) {
        source.getServer().getCommands().performPrefixedCommand(source, command);
        // Minecraft 26.3 returns void; acceptance is provisional and must be world-verified.
        return 1;
    }
}
