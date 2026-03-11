package net.caffeinemc.mods.lithium.mixin.ai.poi.reduce_poi_memory.poi_unloading;

import net.caffeinemc.mods.lithium.common.world.interests.PoiUnloading;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow
    @Final
    private PoiManager poiManager;

    // Inject after the chunkMap save because PoiManager::flush is called
    @Inject(method = "method_60440", at = @At(value = "INVOKE", target = "net/minecraft/server/level/ChunkMap.save (Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", shift = At.Shift.AFTER))
    void unloadChunkPOIs(ChunkHolder chunkHolder, CompletableFuture completableFuture, long chunkPos, CallbackInfo ci) {
        if (((PoiUnloading) this.poiManager).lithium$shouldUnloadChunkPOIs(chunkPos)) {
            ((PoiUnloading) this.poiManager).lithium$unloadChunkPOIs(chunkPos);
        }
    }
}
