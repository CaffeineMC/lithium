package net.caffeinemc.mods.lithium.common.world.interests.iterator;

import net.caffeinemc.mods.lithium.common.world.interests.PointOfInterestSetExtended;
import net.caffeinemc.mods.lithium.common.world.interests.RegionBasedStorageSectionExtended;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;

import java.util.BitSet;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Stream-less fallback for PoiManager::getInChunk in case search is not otherwise optimized
 * @author jcw780
 */
public class SingleChunkPointOfInterestStream extends Spliterators.AbstractSpliterator<PoiRecord> {
    final int chunkX;
    final int chunkZ;
    final int chunkYMin;
    RegionBasedStorageSectionExtended<PoiSection> storage;
    final BitSet chunkPoiSections;
    int nextPoiSectionIndex;
    Iterator<PoiRecord> sectionIterator;
    private final Predicate<Holder<PoiType>> typeFilter;
    private final PoiManager.Occupancy status;

    public SingleChunkPointOfInterestStream(Predicate<Holder<PoiType>> typeFilter, ChunkPos chunkPos, PoiManager.Occupancy status, RegionBasedStorageSectionExtended<PoiSection> storage) {
        super(Long.MAX_VALUE, Spliterator.ORDERED);
        this.chunkX = chunkPos.x;
        this.chunkZ = chunkPos.z;
        this.storage = storage;

        this.chunkYMin = this.storage.lithium$getChunkYMin();

        this.chunkPoiSections = this.storage.lithium$getNonEmptyPOISections(this.chunkX, this.chunkZ);
        this.typeFilter = typeFilter;
        this.status = status;
    }

    @Override
    public boolean tryAdvance(Consumer<? super PoiRecord> action) {
        do {
            while (this.sectionIterator != null && this.sectionIterator.hasNext()) {
                PoiRecord next = this.sectionIterator.next();
                if (status.getTest().test(next)) {
                    action.accept(next);
                    return true;
                }
            }

        } while (this.nextSection());
        return false;
    }

    private boolean nextSection() {
        int nextSectionIndex;
        while ((nextSectionIndex = this.chunkPoiSections.nextSetBit(this.nextPoiSectionIndex)) != -1) {
            this.nextPoiSectionIndex = nextSectionIndex + 1;
            int chunkY = nextSectionIndex + this.chunkYMin;
            this.sectionIterator = this.getSectionIterator(this.chunkX, chunkY, this.chunkZ);
            if (this.sectionIterator != null && this.sectionIterator.hasNext()) {
                return true;
            }
        }
        return false;
    }

    private Iterator<PoiRecord> getSectionIterator(int chunkX, int chunkY, int chunkZ) {
        // Note: chunk is already "POI loaded" by lithium$getNonEmptyPOISections
        PoiSection poiSection = this.storage.lithium$uncheckedGetElementAt(SectionPos.asLong(chunkX, chunkY, chunkZ)).orElse(null);
        if (poiSection == null) {
            return null;
        }
        return ((PointOfInterestSetExtended) poiSection).lithium$iterate(this.typeFilter);
    }
}
