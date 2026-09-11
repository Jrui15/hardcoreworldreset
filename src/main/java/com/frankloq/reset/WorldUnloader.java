package com.frankloq.reset;

import com.frankloq.HardcoreWorldReset;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import java.util.List;

public class WorldUnloader {

    // The three dimensions we want to reset.
    public static final List<ResourceKey<Level>> RESET_DIMENSIONS = List.of(
            Level.OVERWORLD,
            Level.NETHER,
            Level.END
    );

    // Saves all chunks in all three target worlds and
    // forces them to be written to disk.
    // This MUST be called on the main server thread.
    // @param server the running MinecraftServer instance
    public static void saveAndUnloadWorlds(MinecraftServer server) {
        HardcoreWorldReset.LOGGER.info("Beginning world save and unload...");

        for (ResourceKey<Level> key : RESET_DIMENSIONS) {
            ServerLevel world = server.getLevel(key);

            if (world == null) {
                HardcoreWorldReset.LOGGER.warn(
                        "World {} was null during unload, skipping.",
                        key.identifier()
                );
                continue;
            }

            HardcoreWorldReset.LOGGER.info(
                    "Saving world: {}",
                    key.identifier()
            );

            try {
                // Save all chunks that are currently loaded.
                // The "true" argument means flush = write everything to disk now.
                // The "false" argument means don't log the save to console
                // (we handle our own logging)
                world.save(null, true, false);

                HardcoreWorldReset.LOGGER.info(
                        "Successfully saved world: {}",
                        key.identifier()
                );

            } catch (Exception e) {
                HardcoreWorldReset.LOGGER.error(
                        "Failed to save world {}: {}",
                        key.identifier(),
                        e.getMessage()
                );
            }
        }

        HardcoreWorldReset.LOGGER.info("All worlds saved.");
    }
}