package com.frankloq.reset;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

public class WorldSpawnLocator {

    public static BlockPos determineWorldSpawn(ServerLevel world) {
        BlockPos bestPos = null;

        // Bound synchronous chunk generation so ocean seeds cannot stall the server watchdog.
        for (int r = 0; r <= 32; r += 16) {

            // Calculate how many points we need to check to form a perfect circle at this radius
            int points = (r == 0) ? 1 : (int) ((2 * Math.PI * r) / 16);

            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI / points) * i;
                int x = (int) (Math.cos(angle) * r);
                int z = (int) (Math.sin(angle) * r);

                BlockPos candidate = checkAndGetSafePos(world, x, z);
                if (candidate != null) {
                    bestPos = candidate;
                    break;
                }
            }
            if (bestPos != null) break;
        }

        return bestPos != null ? bestPos : createFallback(world, 0, 0);
    }

    public static BlockPos createFallback(ServerLevel world, int x, int z) {
        int y = Math.min(world.getMaxY() - 2,
                Math.max(world.getMinY() + 2, world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)));
        BlockPos pos = new BlockPos(x, y, z);
        world.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        return pos;
    }

    public static BlockPos checkAndGetSafePos(ServerLevel world, int x, int z) {
        // We load the chunk at the target X, Z to scan it safely
        world.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);

        // Calculate the starting coordinates of the chunk
        int chunkStartX = (x >> 4) << 4;
        int chunkStartZ = (z >> 4) << 4;

        // Scan multiple blocks inside the loaded chunk
        // We check a grid of points within the 16x16 chunk. If any of them are safe, we use it
        for (int dx = 2; dx < 16; dx += 4) {
            for (int dz = 2; dz < 16; dz += 4) {
                int checkX = chunkStartX + dx;
                int checkZ = chunkStartZ + dz;

        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, checkX, checkZ);

                // Anti-void protection
                if (y <= world.getMinY()) {
                    continue;
                }

                BlockPos fuzzyPos = new BlockPos(checkX, y, checkZ);

                if (isValidSpawnBlock(world, fuzzyPos)) {
                    return fuzzyPos; // Found a safe solid block!
                }
            }
        }

        return null; // The entire chunk grid was unsafe
    }

    // Definitive fix for the player spawning crawling or in dangerous blocks
    private static boolean isValidSpawnBlock(ServerLevel world, BlockPos pos) {
        BlockState under = world.getBlockState(pos.below());
        BlockState feet = world.getBlockState(pos);
        BlockState head = world.getBlockState(pos.above());

        // The floor must be a solid block and not water or lava
        if (!under.isRedstoneConductor(world, pos.below()) || !world.getFluidState(pos.below()).isEmpty()) {
            return false;
        }

        // Paranoid check to ensure the block the player is standing in is definitely not a fluid
        if (!world.getFluidState(pos).isEmpty() || !world.getFluidState(pos.above()).isEmpty()) {
            return false;
        }

        // No dangerous blocks allowed. Reject powder snow, magma and cactus
        if (under.is(Blocks.POWDER_SNOW) || feet.is(Blocks.POWDER_SNOW) || head.is(Blocks.POWDER_SNOW)) return false;
        if (under.is(Blocks.MAGMA_BLOCK) || under.is(Blocks.CACTUS) || under.is(Blocks.CAMPFIRE)) return false;

        // The crawling prevention, the collision shape for the feet and head must be completely empty
        if (!feet.getCollisionShape(world, pos).isEmpty()) return false;
        if (!head.getCollisionShape(world, pos.above()).isEmpty()) return false;

        return true;
    }
}
