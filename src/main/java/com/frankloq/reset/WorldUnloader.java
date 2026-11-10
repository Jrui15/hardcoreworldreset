package com.frankloq.reset;

import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Only vanilla worlds are erased; limbo and other mods' dimensions are retained. */
public final class WorldUnloader {
    private WorldUnloader() {}
    public static final List<ResourceKey<Level>> RESET_DIMENSIONS =
            List.of(Level.OVERWORLD, Level.NETHER, Level.END);
}
