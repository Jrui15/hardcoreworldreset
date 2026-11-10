package com.frankloq.reset;

import com.frankloq.HardcoreWorldReset;
import com.frankloq.LimboDimension;
import com.frankloq.mixin.MinecraftServerAccessor;
import com.frankloq.mixin.ServerLevelAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ServerLevelData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.OptionalLong;

public class WorldResetManager {
    private static ResetPhase currentPhase = ResetPhase.IDLE;
    private static int phaseTimer;
    private static boolean countdownLocked;
    private static int currentTry = 1;
    private static boolean hasLoadedTryCount;

    public static boolean isResetting() { return currentPhase != ResetPhase.IDLE; }
    public static boolean tryLockCountdown() {
        if (countdownLocked || isResetting()) return false;
        countdownLocked = true;
        return true;
    }
    public static void unlockCountdown() { countdownLocked = false; }
    public static void resetSession() {
        currentPhase = ResetPhase.IDLE;
        countdownLocked = false;
        phaseTimer = 0;
        currentTry = 1;
        hasLoadedTryCount = false;
    }
    public static int getCurrentTry(MinecraftServer server) {
        if (!hasLoadedTryCount) {
            Path file = server.getWorldPath(LevelResource.ROOT).resolve("hardcore_state.txt");
            try {
                currentTry = Files.exists(file) ? Math.max(1, Integer.parseInt(Files.readString(file).trim())) : 1;
            } catch (IOException | NumberFormatException e) {
                HardcoreWorldReset.LOGGER.warn("Unable to load attempt counter", e);
                currentTry = 1;
            }
            hasLoadedTryCount = true;
        }
        return currentTry;
    }
    public static void beginReset(MinecraftServer server) {
        if (isResetting()) return;
        getCurrentTry(server);
        currentPhase = ResetPhase.UNLOADING;
        phaseTimer = 40;
        server.getPlayerList().broadcastSystemMessage(Component.literal("§5[Reset] §7Beginning world erasure..."), false);
    }
    public static void tick(MinecraftServer server) {
        if (!isResetting() || phaseTimer-- > 0) return;
        if (currentPhase == ResetPhase.UNLOADING) {
            // Replace every level at END_SERVER_TICK, before the next tick or save.
            try {
                rebuildWorlds(server);
                currentPhase = ResetPhase.DONE;
                phaseTimer = 40;
            } catch (Exception e) {
                HardcoreWorldReset.LOGGER.error("World reset failed; stopping the server", e);
                server.halt(false);
            }
        } else if (currentPhase == ResetPhase.DONE) {
            try {
                HardcoreWorldReset.onResetComplete(server);
                server.saveEverything(true, true, true);
                Files.deleteIfExists(server.getWorldPath(LevelResource.ROOT).resolve("hardcore_reset_in_progress"));
                currentPhase = ResetPhase.IDLE;
                countdownLocked = false;
            } catch (Exception e) {
                HardcoreWorldReset.LOGGER.error("Player recovery failed; stopping the server", e);
                server.halt(false);
            }
        }
    }
    private static void rebuildWorlds(MinecraftServer server) throws IOException {
        for (var player : server.getPlayerList().getPlayers()) {
            if (!player.level().dimension().equals(LimboDimension.LIMBO_KEY)) {
                throw new IllegalStateException("All players must be in limbo before resetting");
            }
        }
        MinecraftServerAccessor access = (MinecraftServerAccessor) server;
        var oldLevels = new LinkedHashMap<ResourceKey<Level>, ServerLevel>();
        for (var key : WorldUnloader.RESET_DIMENSIONS) {
            ServerLevel level = server.getLevel(key);
            if (level != null) oldLevels.put(key, level);
        }
        if (!oldLevels.containsKey(Level.OVERWORLD)) throw new IllegalStateException("Missing overworld");
        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        for (var key : oldLevels.keySet()) {
            Path dimension = access.hwr$storage().getDimensionPath(key);
            for (String folder : new String[]{"region", "entities", "poi", "data"}) validateTree(root, dimension.resolve(folder));
        }
        for (var resource : new LevelResource[]{LevelResource.PLAYER_ADVANCEMENTS_DIR,
                LevelResource.PLAYER_STATS_DIR, LevelResource.PLAYER_DATA_DIR}) {
            validateTree(root, server.getWorldPath(resource));
        }
        server.saveEverything(true, true, true);
        Files.writeString(root.resolve("hardcore_reset_in_progress"),
                "Reset interrupted. Restore a backup or inspect all dimensions before removing this file.",
                java.nio.file.StandardOpenOption.CREATE_NEW);
        for (var level : oldLevels.values()) level.close();
        for (var key : oldLevels.keySet()) {
            Path dimension = access.hwr$storage().getDimensionPath(key);
            for (String folder : new String[]{"region", "entities", "poi", "data"}) deleteFolder(root, dimension.resolve(folder));
        }
        for (var resource : new LevelResource[]{LevelResource.PLAYER_ADVANCEMENTS_DIR,
                LevelResource.PLAYER_STATS_DIR, LevelResource.PLAYER_DATA_DIR}) {
            Path path = server.getWorldPath(resource);
            deleteFolder(root, path);
            Files.createDirectories(path);
        }
        // Discard offline caches so reconnecting players cannot resurrect old progress.
        var playerList = server.getPlayerList();
        var playerAccess = (com.frankloq.mixin.PlayerListAccessor) playerList;
        playerAccess.hwr$stats().keySet().removeIf(id -> playerList.getPlayer(id) == null);
        playerAccess.hwr$advancements().keySet().removeIf(id -> playerList.getPlayer(id) == null);
        WorldGenSettings previous = server.getWorldGenSettings();
        long seed = HardcoreWorldReset.reuseSeed ? previous.options().seed() : new java.util.Random().nextLong();
        WorldGenSettings settings = new WorldGenSettings(previous.options().withSeed(OptionalLong.of(seed)), previous.dimensions());
        access.hwr$setWorldGenSettings(settings);
        server.getDataStorage().set(WorldGenSettings.TYPE, settings);
        settings.setDirty();
        for (var entry : oldLevels.entrySet()) {
            var key = entry.getKey();
            ServerLevel old = entry.getValue();
            LevelStem stem = settings.dimensions().get(ResourceKey.create(Registries.LEVEL_STEM, key.identifier())).orElseThrow();
            ServerLevel fresh = new ServerLevel(server, access.hwr$executor(), access.hwr$storage(),
                    (ServerLevelData) old.getLevelData(), key, stem, server.getWorldData().isDebugWorld(),
                    BiomeManager.obfuscateSeed(seed), ((ServerLevelAccessor) old).hwr$customSpawners(), key.equals(Level.OVERWORLD));
            access.hwr$levels().put(key, fresh);
            server.getPlayerList().addWorldborderListener(fresh);
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = WorldSpawnLocator.determineWorldSpawn(overworld);
        server.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, spawn, 0.0f, 0.0f));
        server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).listElements()
                .forEach(clock -> server.clockManager().setTotalTicks(clock, 0L));
        overworld.resetWeatherCycle();
        currentTry++;
        Files.writeString(root.resolve("hardcore_state.txt"), Integer.toString(currentTry));
        server.saveEverything(true, true, true);
        HardcoreWorldReset.LOGGER.info("Recreated vanilla dimensions with seed {} (attempt {})", seed, currentTry);
    }
    private static void validateTree(Path root, Path path) throws IOException {
        validatePath(root, path);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        try (var entries = Files.walk(path)) {
            for (var entry : entries.toList()) validatePath(root, entry);
        }
    }
    private static void validatePath(Path root, Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.equals(root) || !absolute.startsWith(root)) throw new IOException("Invalid reset path: " + path);
        for (Path part = absolute; part != null && !part.equals(root); part = part.getParent()) {
            if (Files.isSymbolicLink(part) || (Files.exists(part) && !part.toRealPath().startsWith(root.toRealPath()))) {
                throw new IOException("Reset path escapes the world directory: " + path);
            }
        }
    }
    private static void deleteFolder(Path root, Path path) throws IOException {
        validatePath(root, path);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        try (var walk = Files.walk(path)) {
            for (Path entry : walk.sorted(Comparator.reverseOrder()).toList()) {
                validatePath(root, entry);
                Files.delete(entry);
            }
        }
    }
}
