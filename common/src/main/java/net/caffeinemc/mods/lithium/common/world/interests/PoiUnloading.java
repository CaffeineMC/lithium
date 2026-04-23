package net.caffeinemc.mods.lithium.common.world.interests;

public interface PoiUnloading {
    boolean lithium$shouldUnloadChunkPOIs(long chunkPos);

    void lithium$unloadChunkPOIs(long chunkPos);
}
