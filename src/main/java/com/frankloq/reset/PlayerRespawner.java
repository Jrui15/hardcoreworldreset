package com.frankloq.reset;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class PlayerRespawner {

    public static void respawnAllPlayers(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        // Fetch the calculated natural world spawn point
        BlockPos worldSpawn = overworld.getRespawnData().pos();

        net.minecraft.commands.CommandSourceStack silentSource = server.createCommandSourceStack().withSuppressedOutput();

        server.getCommands().performPrefixedCommand(silentSource, "clear @a");

        server.getCommands().performPrefixedCommand(silentSource, "weather clear");

        server.getCommands().performPrefixedCommand(silentSource, "recipe take @a *");

        server.getCommands().performPrefixedCommand(silentSource, "recipe give @a minecraft:crafting_table");

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            WorldInjectionUtils.wipePlayerState(player);

            // Use AdvancementEntry instead of Advancement for it to work in 1.20.2
            for (net.minecraft.advancements.AdvancementHolder advancementEntry : server.getAdvancements().getAllAdvancements()) {
                net.minecraft.advancements.AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancementEntry);
                if (progress.hasProgress()) {
                    java.util.List<String> obtainedCriteria = new java.util.ArrayList<>();
                    progress.getCompletedCriteria().forEach(obtainedCriteria::add);
                    for (String criterion : obtainedCriteria) {
                        player.getAdvancements().revoke(advancementEntry, criterion);
                    }
                }
            }

            player.setExperienceLevels(0);
            player.setExperiencePoints(0);
            player.experienceProgress = 0.0f;
            player.totalExperience = 0;

            player.removeAllEffects();

            player.setHealth(20.0f);
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(5.0f);
            player.setRemainingFireTicks(0);
            player.setTicksFrozen(0);
            player.setAirSupply(player.getMaxAirSupply());
            player.fallDistance = 0.0f;

            // Delete their old spawnpoint
            player.setRespawnPosition(null, false);

            // Use the chunk scanner to find a perfectly safe block near the spawn
            net.minecraft.core.BlockPos fuzzySpawn = com.frankloq.reset.WorldSpawnLocator.checkAndGetSafePos(overworld, worldSpawn.getX(), worldSpawn.getZ());

            // Fallback If the scanner somehow returns null (bc someone flooded the spawn with lava idk)
            if (fuzzySpawn == null) {
                int fallbackY = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldSpawn.getX(), worldSpawn.getZ());
                if (fallbackY <= overworld.getMinY()) fallbackY = 64; // Anti-void protection
                fuzzySpawn = new net.minecraft.core.BlockPos(worldSpawn.getX(), fallbackY, worldSpawn.getZ());
            }

            // Force load the specific chunk to prevent suffocation
            overworld.getChunkSource().getChunk(fuzzySpawn.getX() >> 4, fuzzySpawn.getZ() >> 4, ChunkStatus.FULL, true);

            // Drop them safely onto the surface
            player.teleportTo(
                    overworld,
                    fuzzySpawn.getX() + 0.5,
                    fuzzySpawn.getY() + 0.1,
                    fuzzySpawn.getZ() + 0.5, java.util.Set.of(), 0.0f, 0.0f, true
            );

            player.setGameMode(GameType.SURVIVAL);
        }
    }
}
