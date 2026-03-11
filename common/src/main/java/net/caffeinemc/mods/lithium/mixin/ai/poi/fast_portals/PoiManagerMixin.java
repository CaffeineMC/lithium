package net.caffeinemc.mods.lithium.mixin.ai.poi.fast_portals;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.caffeinemc.mods.lithium.common.util.Pos;
import net.caffeinemc.mods.lithium.common.world.interests.RegionBasedStorageSectionExtended;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.*;

import java.util.BitSet;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

@Mixin(PoiManager.class)
public abstract class PoiManagerMixin extends SectionStorage<PoiSection, PoiSection.Packed> implements RegionBasedStorageSectionExtended<PoiSection> {

    @Shadow
    @Final
    private LongSet loadedChunks;

    @Unique
    private final LongSet preloadedCenterChunks = new LongOpenHashSet();
    @Unique
    private int preloadRadius = 0;

    public PoiManagerMixin(SimpleRegionStorage simpleRegionStorage, Codec<PoiSection.Packed> codec, Function<PoiSection, PoiSection.Packed> function, BiFunction<PoiSection.Packed, Runnable, PoiSection> biFunction, Function<Runnable, PoiSection> function2, RegistryAccess registryAccess, ChunkIOErrorReporter chunkIOErrorReporter, LevelHeightAccessor levelHeightAccessor) {
        super(simpleRegionStorage, codec, function, biFunction, function2, registryAccess, chunkIOErrorReporter, levelHeightAccessor);
    }


    /**
     * @author Crec0, 2No2Name
     * @reason Streams in this method cause unnecessary lag. Simply rewriting this to not use streams, we gain
     * considerable performance. Noticeable when large amount of entities are traveling through nether portals.
     * Furthermore, caching whether all surrounding chunks are loaded is more efficient than caching the state
     * of single chunks only.
     *
     * @author jcw780
     * @reason Correct logic to match vanilla chunk loading conditions and order.
     * For a chunk to be loaded, the chunk must have at least one section that either has no POISection or the POISection
     * is not valid.
     * Vanilla iterates sections by x, y and then z. In order to use the Lithium column lookup, we need to sort by the
     * lowest y section then x in each z row.
     */
    @Overwrite
    public void ensureLoadedAndValid(LevelReader worldView, BlockPos pos, int radius) {
        if (this.preloadRadius != radius) {
            //Usually there is only one preload radius per PointOfInterestStorage. Just in case another mod adjusts it dynamically, we avoid
            //assuming its value.
            this.preloadedCenterChunks.clear();
            this.preloadRadius = radius;
        }
        long chunkPos = ChunkPos.asLong(pos);
        if (this.preloadedCenterChunks.contains(chunkPos)) {
            return;
        }
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());

        int chunkRadius = Math.floorDiv(radius, 16);
        long[] sectionsYXPacked = new long[2 * chunkRadius + 1];
        final int maxYSectionIndexExclusive = Pos.SectionYIndex.getMaxYSectionIndexExclusive(worldView);
        for (int z = chunkZ - chunkRadius, zMax = chunkZ + chunkRadius; z <= zMax; z++) {
            int loadingChunkCounter = 0;
            for (int x = chunkX - chunkRadius, xMax = chunkX + chunkRadius; x <= xMax; x++) {
                final int lowestSection  = this.lithium$getLowestEmptyOrInvalidSection(worldView, x, z);
                // Flip the check for poi unloading so portal chunk POIs are never unloaded
                // This prevents an edgecase where all sections are loaded and valid in chunk being unloaded despite
                // being portal loaded - which creates additional lag on subsequent tp's after unloading.
                // Note: This technically means that loaded chunks may have different contents but literally nothing
                // else in minecraft uses this and it does not affect the behavior of this method.
                if (loadedChunks.add(ChunkPos.asLong(x, z)) && lowestSection < maxYSectionIndexExclusive) {
                    sectionsYXPacked[loadingChunkCounter++] = packYX(lowestSection, x);
                }
            }
            //Sort by signed Y, signed X as tie-break
            LongArrays.quickSort(sectionsYXPacked, 0, loadingChunkCounter);
            for (int chunkIndex = 0; chunkIndex < loadingChunkCounter; chunkIndex++) {
                final long packedYX = sectionsYXPacked[chunkIndex];
                worldView.getChunk(unpackX(packedYX), z, ChunkStatus.EMPTY);
            }
        }
        this.preloadedCenterChunks.add(chunkPos);
    }

    @Unique
    private static int unpackX(long packedYX) {
        return (int) ((packedYX & 0xFFFF_FFFFL) + Integer.MIN_VALUE);
    }

    /**
     * Pack YX for sorting, two's complement conversion applied for sorting by signed X.
     */
    @Unique
    private static long packYX(long y, long x) {
        return (y << 32) | (x - Integer.MIN_VALUE);
    }

    @Unique
    private int lithium$getLowestEmptyOrInvalidSection(LevelReader worldView, int x, int z) {
        final BitSet column = this.lithium$getNonEmptyPOISections(x, z);
        int lowestUnsetSection = column.nextClearBit(0);
        int setSectionIndex = -1;
        while ((setSectionIndex = column.nextSetBit(setSectionIndex + 1)) != -1
                && setSectionIndex < lowestUnsetSection) {
            Optional<PoiSection> section = this.lithium$uncheckedGetElementAt(
                    SectionPos.asLong(x, Pos.SectionYCoord.fromSectionIndex(worldView, setSectionIndex), z)
            );

            if (section.isPresent() && !section.get().isValid()) {
                return setSectionIndex;
            }
        }

        return lowestUnsetSection;
    }
}
