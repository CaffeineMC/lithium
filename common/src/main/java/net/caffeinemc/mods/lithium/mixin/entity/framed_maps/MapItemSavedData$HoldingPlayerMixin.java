package net.caffeinemc.mods.lithium.mixin.entity.framed_maps;

import net.caffeinemc.mods.lithium.common.world.mapdata.MapObservingPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MapItemSavedData.HoldingPlayer.class)
public class MapItemSavedData$HoldingPlayerMixin implements MapObservingPlayer {

    @Shadow
    @Final
    public Player player;

    @Unique
    private boolean hasMapInInventory;

    //Integer time is fine, as incorrectly assuming a player cannot see a map only leads to unnecessary data invalidation and extra packets sent.
    @Unique
    private int lastSeenMapInItemFrame = Integer.MIN_VALUE;

    @Override
    public boolean lithium$requiresMapUpdates() {
        return !this.player.isRemoved() && (this.hasMapInInventory || lithium$canMaybeSeeMapInItemFrame());
    }

    @Override
    public void lithium$setHasMapInInventory(boolean hasMapInInventory) {
        this.hasMapInInventory = hasMapInInventory;
    }

    @Override
    public boolean lithium$canMaybeSeeMapInItemFrame() {
        if (this.player.level() instanceof ServerLevel serverLevel) {
            int ticksNotSeen = serverLevel.getServer().getTickCount() - this.lastSeenMapInItemFrame;
            return ticksNotSeen >= 0 && ticksNotSeen < 20;
        }
        return false;
    }

    @Override
    public void lithium$setCanSeeMapInItemFrame() {
        if (this.player.level() instanceof ServerLevel serverLevel) {
            this.lastSeenMapInItemFrame = serverLevel.getServer().getTickCount();
        }
    }
}
