package dev.aether0xff.mcworker;

import me.senseiwells.puppet.PuppetPlayer;
import me.senseiwells.puppet.action.ActionModifier;
import me.senseiwells.puppet.action.impl.AttackAction;
import me.senseiwells.puppet.action.impl.UseAction;
import me.senseiwells.puppet.extensions.PlayerActionsExtension;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.Vec3;

/**
 * Typed adapter to PuppetPlayers 26.3.
 *
 * Only spawn uses PuppetPlayers' command API (PuppetPlayers performs an async join).
 * All in-game operations use PuppetPlayer's Java/Kotlin API, not server command strings.
 */
final class PuppetBridge {
    private PuppetBridge() {}

    static void spawn(CommandSourceStack source, String name) {
        // The 26.3 command dispatcher returns void; this only submits a request.
        source.getServer().getCommands().performPrefixedCommand(source, "puppet " + name + " spawn");
    }

    static boolean move(PuppetPlayer player, Vec3 goal) {
        stop(player);
        return player.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0, 0);
    }

    static void lookAt(PuppetPlayer player, Vec3 position) {
        player.getLookControl().setLookAt(position);
    }

    static boolean attackHold(PuppetPlayer player) {
        return PlayerActionsExtension.getActions(player).run(new AttackAction(ActionModifier.Hold));
    }

    static boolean useOnce(PuppetPlayer player) {
        return PlayerActionsExtension.getActions(player).run(new UseAction(ActionModifier.Once));
    }

    static void stop(PuppetPlayer player) {
        PlayerActionsExtension.getActions(player).reset();
        player.getNavigation().stop();
    }
}
