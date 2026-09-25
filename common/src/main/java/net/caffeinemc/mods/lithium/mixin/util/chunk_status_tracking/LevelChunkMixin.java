package net.caffeinemc.mods.lithium.mixin.util.chunk_status_tracking;

import net.caffeinemc.mods.lithium.common.world.chunk.ChunkStatusTracker;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin extends ChunkAccess {

    public LevelChunkMixin(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor, PalettedContainerFactory palettedContainerFactory, long l, @Nullable LevelChunkSection[] levelChunkSections, @Nullable BlendingData blendingData) {
        super(chunkPos, upgradeData, levelHeightAccessor, palettedContainerFactory, l, levelChunkSections, blendingData);
    }

    @Shadow
    public abstract Level getLevel();

    @Shadow
    public abstract FullChunkStatus getFullStatus();

    @Inject(
            method = "setFullStatus(Ljava/util/function/Supplier;)V", at = @At("RETURN")
    )
    private void onChunkFull(Supplier<FullChunkStatus> supplier, CallbackInfo ci) {
        if (supplier != null && this.getLevel() instanceof ServerLevel serverLevel) {
            boolean isAccessible = this.getFullStatus().isOrAfter(FullChunkStatus.FULL);
            if (isAccessible) {
                ChunkStatusTracker.onChunkAccessible(serverLevel, (LevelChunk) (Object) this);
            } else {
                ChunkStatusTracker.onChunkInaccessible(serverLevel, this.getPos());
            }
        }
    }
}
