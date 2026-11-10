package com.frankloq.reset;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** Shared reset and reconnect recovery path. */
public final class PlayerRespawner {
    private PlayerRespawner() {}

    public static void respawnAllPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) respawnPlayer(server, player);
    }

    public static void respawnPlayer(MinecraftServer server, ServerPlayer player) {
        var world = server.overworld();
        var spawn = world.getRespawnData().pos();
        WorldInjectionUtils.wipePlayerState(player);
        player.setRespawnPosition(null, false);
        player.resetRecipes(server.getRecipeManager().getRecipes());
        player.awardRecipesByKey(java.util.List.of(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.RECIPE,
                net.minecraft.resources.Identifier.withDefaultNamespace("crafting_table"))));
        for (var advancement : server.getAdvancements().getAllAdvancements()) {
            var progress = player.getAdvancements().getOrStartProgress(advancement);
            var criteria = new java.util.ArrayList<String>();
            progress.getCompletedCriteria().forEach(criteria::add);
            criteria.forEach(criterion -> player.getAdvancements().revoke(advancement, criterion));
        }
        var safe = WorldSpawnLocator.checkAndGetSafePos(world, spawn.getX(), spawn.getZ());
        if (safe == null) safe = WorldSpawnLocator.createFallback(world, spawn.getX(), spawn.getZ());
        if (!player.teleportTo(world, safe.getX() + 0.5, safe.getY() + 0.1, safe.getZ() + 0.5,
                java.util.Set.of(), 0, 0, true)) {
            throw new IllegalStateException("Unable to return player from limbo: " + player.getUUID());
        }
        player.setGameMode(GameType.SURVIVAL);
    }
}
