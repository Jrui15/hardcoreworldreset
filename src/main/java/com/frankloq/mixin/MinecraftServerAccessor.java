package com.frankloq.mixin;

import java.util.Map;
import java.util.concurrent.Executor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access to server state needed when replacing the vanilla dimensions. */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess hwr$storage();

    @Accessor("executor")
    Executor hwr$executor();

    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> hwr$levels();

    @Mutable
    @Accessor("worldGenSettings")
    void hwr$setWorldGenSettings(WorldGenSettings settings);
}
