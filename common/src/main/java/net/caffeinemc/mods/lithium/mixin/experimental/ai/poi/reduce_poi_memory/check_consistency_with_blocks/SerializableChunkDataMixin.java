package net.caffeinemc.mods.lithium.mixin.experimental.ai.poi.reduce_poi_memory.check_consistency_with_blocks;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.caffeinemc.mods.lithium.common.world.interests.PoiCheckConsistency;
import net.caffeinemc.mods.lithium.common.world.interests.RegionBasedStorageSectionExtended;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.BitSet;

@Mixin(SerializableChunkData.class)
public abstract class SerializableChunkDataMixin {

    // Ensure chunk is "POI-loaded" and retrieve lookup column for reuse to reduce hashmap calls since all calls in the method
    // are in the same chunk.
    // Use specialized method to take advantage of acquired column
    @Redirect(method = "read", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;checkConsistencyWithBlocks(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/LevelChunkSection;)V"))
    private void redirectCheckConsistencyWithBlocks(PoiManager poiManager, SectionPos sectionPos,
                                                    LevelChunkSection levelChunkSection,
                                                    @Local(name = "pos", argsOnly = true) ChunkPos pos,
                                                    @Share("column") LocalRef<BitSet> column) {
        if (column.get() == null) {
            column.set(((RegionBasedStorageSectionExtended<?>) poiManager).lithium$getNonEmptyPOISections(pos.x(), pos.z()));
        }
        ((PoiCheckConsistency<PoiSection>) poiManager).lithium$CheckConsistencyWithBlocks(sectionPos, levelChunkSection, column.get());
    }
}
