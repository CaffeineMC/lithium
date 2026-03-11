package net.caffeinemc.mods.lithium.common.world.interests;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.BitSet;

public interface PoiCheckConsistency<R> {
    R lithium$createSectionAndInsert(long l);
    void lithium$CheckConsistencyWithBlocks(SectionPos sectionPos, LevelChunkSection levelChunkSection, BitSet column);
}
