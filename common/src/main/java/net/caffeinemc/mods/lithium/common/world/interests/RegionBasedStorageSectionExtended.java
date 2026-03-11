package net.caffeinemc.mods.lithium.common.world.interests;

import net.caffeinemc.mods.lithium.common.util.functions.FunLongAnd5;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.BitSet;
import java.util.Optional;
import java.util.function.Predicate;

public interface RegionBasedStorageSectionExtended<R> {

    @Nullable
    public BitSet lithium$getColumn(long chunkPos);

    BitSet lithium$getOrAddColumnIfNull(long chunkPos);

    <S, T, U> U lithium$getFirstInRangeInChunkColumn(int chunkX, int chunkZ,
                                                     long deltaYSqMargin,
                                                     BlockPos center, long radiusSq,
                                                     FunLongAnd5<R, BlockPos, Predicate<Holder<S>>, Predicate<BlockPos>, T, U> sectionMapper,
                                                     Predicate<Holder<S>> predicate, Predicate<BlockPos> filter, T status);

    /**
     * Fast-path for collecting all items in a chunk column. This avoids needing to retrieve items for each sub-chunk
     * individually.
     *
     * @param chunkX The x-coordinate of the chunk column
     * @param chunkZ The z-coordinate of the chunk column
     */
    Iterable<R> lithium$getInChunkColumn(int chunkX, int chunkZ);

    BitSet lithium$getNonEmptyPOISections(int chunkX, int chunkZ);

    /**
     * Manually remove a chunk section column bitset - only used when unloading chunk with no POISections
     *
     * @param chunkPos
     * @return
     */
    BitSet lithium$removeColumn(long chunkPos);

    /**
     * Remove a section from the storage without updating the columns map - column adjustment must be handled manually
     * @param l
     * @return
     */
    Optional<R> lithium$removeSectionWithoutUpdatingColumn(long l);

    int lithium$getChunkYMin();

    int lithium$getChunkYMaxInclusive();

    Optional<R> lithium$uncheckedGetElementAt(long sectionPos);
}
