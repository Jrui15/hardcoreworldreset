package com.frankloq;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class LimboDimension {

    public static final ResourceKey<Level> LIMBO_KEY = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(HardcoreWorldReset.MOD_ID, "limbo")
    );

    // Teleports the players to the Limbo dimension
    // Returns true if successful, false if the dimension was not found.
    public static boolean teleportToLimbo(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();

        if (server == null) {
            HardcoreWorldReset.LOGGER.error("Server is null when trying to teleport to Limbo.");
            return false;
        }

        ServerLevel limboWorld = server.getLevel(LIMBO_KEY);

        if (limboWorld == null) {
            HardcoreWorldReset.LOGGER.error(
                    "Limbo world not found in server world list! " +
                            "Make sure the dimension JSON files are in the correct location."
            );

            // Log all registered worlds to help debug
            HardcoreWorldReset.LOGGER.error("Registered worlds:");
            for (ResourceKey<Level> key : server.levelKeys()) {
                HardcoreWorldReset.LOGGER.error("  - {}", key.identifier());
            }

            return false;
        }

        // We use now 1.21 native teleportation instead of FabricDimensions
        if (player.teleport(new net.minecraft.world.level.portal.TeleportTransition(
                limboWorld, new net.minecraft.world.phys.Vec3(0, 65, 0),
                net.minecraft.world.phys.Vec3.ZERO, 0.0f, 0.0f,
                net.minecraft.world.level.portal.TeleportTransition.PLAY_PORTAL_SOUND)) == null) return false;

        HardcoreWorldReset.LOGGER.info(
                "Teleported {} to Limbo.",
                player.getName().getString()
        );

        return true;
    }
}