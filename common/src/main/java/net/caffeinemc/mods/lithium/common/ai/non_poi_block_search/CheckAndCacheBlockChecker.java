package net.caffeinemc.mods.lithium.common.ai.non_poi_block_search;

import net.caffeinemc.mods.lithium.common.util.collections.FixedChunkAccessSectionBitBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * This is intended to be used to optimize Non-POI block searches by pre-checking the ChunkSections, getting the
 * ChunkAccesses which then allows for returning early or only calling getBlockState in possible ChunkSections.
 * <p>
 * Note: Please correctly specify shouldChunkLoad based on whether the getBlockState in vanilla can chunk load or not.
 * The default in game is that it can. Setting it to true will mean that the search will assume that unloaded chunks
 * may have the target and will chunk load then search them if and when it reaches it.
 *
 * @author jcw780
 */
public class CheckAndCacheBlockChecker {
    private final FixedChunkAccessSectionBitBuffer chunkSections2MaybeContainsMatchingBlock;
    private final LevelReader levelReader;
    public final boolean shouldChunkLoad;
    public final Predicate<BlockState> blockStatePredicate;
    private int unloadedPossibleChunkSections = 0;
    public final int minSectionY;

    public CheckAndCacheBlockChecker(BlockPos origin, int horizontalRangeInclusive, int verticalRangeInclusive, LevelReader levelReader,
                                     Predicate<BlockState> blockStatePredicate, boolean shouldChunkLoad) {
        this.chunkSections2MaybeContainsMatchingBlock = new FixedChunkAccessSectionBitBuffer(origin, horizontalRangeInclusive, verticalRangeInclusive);
        this.levelReader = levelReader;
        this.shouldChunkLoad = shouldChunkLoad;
        this.blockStatePredicate = blockStatePredicate;
        this.minSectionY = levelReader.getMinSection();
    }

    public void initializeChunks() {
        this.initializeChunks(null);
    }

    public void initializeChunks(Consumer<Long> chunkCollector) {
        final boolean nullChunkCollector = chunkCollector == null;
        for (long chunkPos : this.chunkSections2MaybeContainsMatchingBlock.getChunkPosInRange()) {
            int x = ChunkPos.getX(chunkPos);
            int z = ChunkPos.getZ(chunkPos);
            boolean chunkMaybeHas = false;

            //Never load chunks in the first pass to avoid observably altering chunk loading behavior
            //Otherwise full region will be loaded vs partial region if the search finds the block early.
            ChunkAccess chunkAccess = levelReader.getChunk(x, z, ChunkStatus.FULL, false);
            if (chunkAccess != null) {
                this.chunkSections2MaybeContainsMatchingBlock.setChunkAccess(chunkPos, chunkAccess);
                for (int y : this.chunkSections2MaybeContainsMatchingBlock.getSectionYInRange()) {
                    chunkMaybeHas = this.checkChunkSection(chunkAccess, x, y, z) || chunkMaybeHas;
                }
            } else if (this.shouldChunkLoad) {
                /* If the search may chunk load then it is possible that target blocks may be revealed when the search
                 * reaches it. Since we cannot load the chunks and check now, we cannot definitively exclude subchunks
                 * inside the chunk. This means that we must flag subchunks that are within build limit - otherwise air
                 * anyway - for the search.
                 */
                for (int y : this.chunkSections2MaybeContainsMatchingBlock.getSectionYInRange()) {
                    this.chunkSections2MaybeContainsMatchingBlock.setChunkSectionStatus(SectionPos.asLong(x, y, z),
                            !levelReader.isOutsideBuildHeight(SectionPos.sectionToBlockCoord(y)));
                    ++this.unloadedPossibleChunkSections;
                }
                chunkMaybeHas = true;

            }

            if (!nullChunkCollector && chunkMaybeHas) {
                chunkCollector.accept(chunkPos);
            }
        }
    }

    public int getChunkSize(){
        return this.chunkSections2MaybeContainsMatchingBlock.numChunks;
    }

    public boolean hasUnloadedPossibleChunks(){
        return this.unloadedPossibleChunkSections > 0;
    }

    private boolean checkChunkSection(ChunkAccess chunkAccess, int chunkX, int chunkY, int chunkZ) {
        final int chunkSectionYIndex = chunkY - this.minSectionY;
        LevelChunkSection[] chunkSections = chunkAccess.getSections();
        if (chunkSectionYIndex >= 0
                && chunkSectionYIndex < chunkSections.length
                && chunkSections[chunkSectionYIndex].maybeHas(blockStatePredicate)) {
            this.chunkSections2MaybeContainsMatchingBlock.setChunkSectionStatus(
                    SectionPos.asLong(chunkX, chunkY, chunkZ), true);
            return true;
        }
        return false;
    }

    public boolean checkCachedSection(int chunkX, int chunkY, int chunkZ) {
        return this.chunkSections2MaybeContainsMatchingBlock.getChunkSectionBit(chunkX, chunkY, chunkZ);
    }

    public ChunkAccess getCachedChunkAccess(long chunkPos) {
        return this.chunkSections2MaybeContainsMatchingBlock.getChunkAccess(chunkPos);
    }

    public ChunkAccess getCachedChunkAccess(BlockPos blockPos) {
        return this.chunkSections2MaybeContainsMatchingBlock.getChunkAccess(blockPos);
    }

    public boolean shouldStop(){
        return this.chunkSections2MaybeContainsMatchingBlock.hasNoTrueChunkSections();
    }

    public boolean checkPosition(BlockPos blockPos) {
        if(!this.chunkSections2MaybeContainsMatchingBlock.getChunkSectionBit(blockPos)) return false;
        ChunkAccess chunkAccess = this.chunkSections2MaybeContainsMatchingBlock.getChunkAccess(blockPos);
        if(chunkAccess == null) {
            if (!this.shouldChunkLoad) {
                return false;
            }

            final int chunkX = SectionPos.blockToSectionCoord(blockPos.getX());
            final int chunkY = SectionPos.blockToSectionCoord(blockPos.getY());
            final int chunkZ = SectionPos.blockToSectionCoord(blockPos.getZ());
            chunkAccess = levelReader.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            //this chunkAccess cannot be null and reach here because it should throw earlier
            assert chunkAccess != null;
            this.chunkSections2MaybeContainsMatchingBlock.setChunkAccess(blockPos, chunkAccess);
            if (!checkChunkSection(chunkAccess, chunkX, chunkY, chunkZ)) {
                --this.unloadedPossibleChunkSections;
                return false;
            }
        }

        return blockStatePredicate.test(chunkAccess.getBlockState(blockPos));
    }
}
