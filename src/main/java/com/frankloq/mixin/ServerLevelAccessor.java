package com.frankloq.mixin;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CustomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerLevel.class)
public interface ServerLevelAccessor {
    @Accessor("customSpawners")
    List<CustomSpawner> hwr$customSpawners();
}
