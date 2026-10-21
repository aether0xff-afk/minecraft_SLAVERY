package dev.aether0xff.mcworker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorkerCoreMod implements ModInitializer {
    public static final String MOD_ID = "minecraft_slavery_worker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> WorkerCommands.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(WorkerJobs::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WorkerJobs.clear());
        LOGGER.info("WorkerCore loaded (experimental milestone 0)");
    }
}
