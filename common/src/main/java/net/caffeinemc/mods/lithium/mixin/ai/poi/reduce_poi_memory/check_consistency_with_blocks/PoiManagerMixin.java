package net.caffeinemc.mods.lithium.mixin.ai.poi.reduce_poi_memory.check_consistency_with_blocks;

import net.caffeinemc.mods.lithium.common.world.interests.PoiCheckConsistency;
import net.caffeinemc.mods.lithium.common.world.interests.RegionBasedStorageSectionExtended;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.BitSet;
import java.util.Optional;
import java.util.function.BiConsumer;

@Mixin(PoiManager.class)
public abstract class PoiManagerMixin implements PoiCheckConsistency<PoiSection>, RegionBasedStorageSectionExtended<PoiSection> {
    @Shadow
    protected abstract void updateFromSection(LevelChunkSection levelChunkSection, SectionPos sectionPos, BiConsumer<BlockPos, Holder<PoiType>> biConsumer);

    @Shadow
    private static boolean mayHavePoi(LevelChunkSection levelChunkSection) {
        return false;
    }

    /**
     * Copy the logic of PoiManager::checkConsistencyWithBlocks except using Lithium columns lookup
     * @param sectionPos
     * @param levelChunkSection
     * @param column
     */
    @Override
    public void lithium$CheckConsistencyWithBlocks(SectionPos sectionPos, LevelChunkSection levelChunkSection, BitSet column) {
        final int chunkYMin = this.lithium$getChunkYMin();
        final int currentYSectionIndex = sectionPos.y() - chunkYMin;
        if (column.get(currentYSectionIndex)) {
            // Chunk should already be unpacked - do not need to check further
            Optional<PoiSection> optional = this.lithium$uncheckedGetElementAt(sectionPos.asLong());
            if (optional.isPresent()) {
                PoiSection section = optional.get();
                section.refresh(biConsumer -> {
                    if (mayHavePoi(levelChunkSection)) {
                        this.updateFromSection(levelChunkSection, sectionPos, biConsumer);
                    }
                });
            } else {
                throw new IllegalStateException(String.format("Section %d %d %d is missing from storage despite being marked as present", sectionPos.x(), sectionPos.y(), sectionPos.z()));
            }
        } else {
            if (mayHavePoi(levelChunkSection)) {
                // Section is empty - always create new PoiSection
                PoiSection section = this.lithium$createSectionAndInsert(sectionPos.asLong());
                this.updateFromSection(levelChunkSection, sectionPos, section::add);
            }
        }
    }
}
