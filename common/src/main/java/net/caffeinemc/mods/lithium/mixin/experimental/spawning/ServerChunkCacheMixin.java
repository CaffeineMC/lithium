package net.caffeinemc.mods.lithium.mixin.experimental.spawning;

import net.caffeinemc.mods.lithium.common.world.ChunkAwareEntityIterable;
import net.caffeinemc.mods.lithium.common.world.WorldHelper;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheMixin {

    @Redirect(
            method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;JLjava/util/List;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getAllEntities()Ljava/lang/Iterable;"
            )
    )
    private Iterable<Entity> iterateEntitiesChunkAware(ServerLevel serverWorld) {
        //noinspection unchecked
        return ((ChunkAwareEntityIterable<Entity>) WorldHelper.getServerEntityCache(serverWorld)).lithium$IterateEntitiesInTrackedSections();
    }
}
