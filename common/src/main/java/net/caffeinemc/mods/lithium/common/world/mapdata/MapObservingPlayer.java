package net.caffeinemc.mods.lithium.common.world.mapdata;

public interface MapObservingPlayer {
    boolean lithium$requiresMapUpdates();

    void lithium$setHasMapInInventory(boolean hasMapInInventory);

    boolean lithium$canSeeMapInItemFrame();

    void lithium$setCanSeeMapInItemFrame();
}
