package net.caffeinemc.mods.lithium.common.ai.pathing;

import net.caffeinemc.mods.lithium.common.block.BlockCountingSection;
import net.caffeinemc.mods.lithium.common.block.BlockStateFlags;
import net.caffeinemc.mods.lithium.common.util.Pos;
import net.caffeinemc.mods.lithium.common.world.ChunkView;
import net.caffeinemc.mods.lithium.common.world.WorldHelper;
import net.caffeinemc.mods.lithium.mixin.ai.pathing.PathfindingContextAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.Nullable;

public abstract class PathNodeCache {
    private static boolean isChunkSectionDangerousNeighbor(LevelChunkSection section) {
        return section.getStates()
                .maybeHas(state -> getNeighborPathNodeType(state) != PathType.OPEN); //type may be null -> is dangerous as fallback
    }

    public static @Nullable PathType getPathNodeType(BlockState state) {
        return ((BlockStatePathingCache) state).lithium$getPathNodeType();
    }

    public static @Nullable PathType getNeighborPathNodeType(BlockBehaviour.BlockStateBase state) {
        return ((BlockStatePathingCache) state).lithium$getNeighborPathNodeType();
    }

    /**
     * Returns whether a chunk section is free of dangers. This makes use of a caching layer to greatly
     * accelerate neighbor danger checks when path-finding.
     *
     * @param section The chunk section to test for dangers
     * @return True if this neighboring section is free of any dangers, otherwise false if it could
     * potentially contain dangers
     */
    public static boolean isSectionSafeAsNeighbor(LevelChunkSection section) {
        // Empty sections can never contribute a danger
        if (section.hasOnlyAir()) {
            return true;
        }

        if (BlockStateFlags.ENABLED) {
            return !((BlockCountingSection) section).lithium$mayContainAny(BlockStateFlags.PATH_NOT_OPEN);
        }
        return !isChunkSectionDangerousNeighbor(section);
    }


    public static PathType getNodeTypeFromNeighbors(PathfindingContext context, int x, int y, int z, PathType fallback) {
        BlockGetter world = context.level();

        LevelChunkSection section = null;

        // Check that all the block's neighbors are within the same chunk column. If so, we can isolate all our block
        // reads to just one chunk and avoid hits against the server chunk manager.
        if (world instanceof ChunkView chunkView && WorldHelper.areNeighborsWithinSameChunkSection(x, y, z)) {
            // If the y-coordinate is within bounds, we can cache the chunk section. Otherwise, the if statement to check
            // if the cached chunk section was initialized will early-exit.
            if (!world.isOutsideBuildHeight(y)) {
                ChunkAccess chunk = chunkView.lithium$getLoadedChunk(Pos.ChunkCoord.fromBlockCoord(x), Pos.ChunkCoord.fromBlockCoord(z));

                // If the chunk is absent, the cached section above will remain null, as there is no chunk section anyway.
                // An empty chunk or section will never pose any danger sources, which will be caught later.
                if (chunk != null) {
                    section = chunk.getSections()[Pos.SectionYIndex.fromBlockCoord(world, y)];
                }
            }

            // If we can guarantee that blocks won't be modified while the cache is active, try to see if the chunk
            // section is empty or contains any dangerous blocks within the palette. If not, we can assume any checks
            // against this chunk section will always fail, allowing us to fast-exit.
            if (section == null || PathNodeCache.isSectionSafeAsNeighbor(section)) {
                return fallback; //TODO side effects of vanilla's path node caching (PathTypeCache maybe has broken invalidation for barely loaded chunks)
            }
        }

        int xStart = x - 1;
        int yStart = y - 1;
        int zStart = z - 1;

        int xEnd = x + 1;
        int yEnd = y + 1;
        int zEnd = z + 1;

        // Vanilla iteration order is XYZ
        for (int adjX = xStart; adjX <= xEnd; adjX++) {
            for (int adjY = yStart; adjY <= yEnd; adjY++) {
                for (int adjZ = zStart; adjZ <= zEnd; adjZ++) {
                    // Skip the vertical column of the origin block
                    if (adjX == x && adjZ == z) {
                        continue;
                    }

                    BlockState state;

                    // If we're not accessing blocks outside a given section, we can greatly accelerate block state
                    // retrieval by calling upon the cached chunk directly.
                    if (section != null) {
                        state = section.getBlockState(adjX & 15, adjY & 15, adjZ & 15);
                    } else {
                        BlockPos.MutableBlockPos pos = ((PathfindingContextAccessor) context).getLastNodePos().set(adjX, adjY, adjZ);
                        state = world.getBlockState(pos);
                    }

                    if (state.isAir()) {
                        continue;
                    }

                    PathType neighborType = PathNodeCache.getNeighborPathNodeType(state);

                    if (neighborType == null) { //Here null means that no path node type is cached (uninitialized or dynamic)
                        //Passing null as previous node type to the method signals to other lithium mixins that we only want the neighbor behavior of this block and not its neighbors
                        neighborType = WalkNodeEvaluator.checkNeighbourBlocks(context, adjX + 1, adjY + 1, adjZ + 1, null);
                        //Here null means that the path node type is not changed by the block!
                        if (neighborType == null) {
                            neighborType = PathType.OPEN;
                        }
                    }
                    if (neighborType != PathType.OPEN) {
                        return neighborType;
                    }
                }
            }
        }

        return fallback;
    }

}
