package net.caffeinemc.mods.lithium.mixin.entity.framed_maps;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.caffeinemc.mods.lithium.common.world.mapdata.MapObservingPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Mixin(MapItemSavedData.class)
public abstract class MapItemSavedDataMixin extends SavedData {

    @Shadow
    @Final
    private Map<Player, MapItemSavedData.HoldingPlayer> carriedByPlayers;

    @Shadow
    @Final
    private List<MapItemSavedData.HoldingPlayer> carriedBy;

    @Unique
    private int lastUpdatedAllPlayers = Integer.MIN_VALUE;


    //No need to mark dirty and send update packets if removing didn't actually modify anything
    @WrapWithCondition(
            method = "removeDecoration",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;setDecorationsDirty()V")
    )
    private boolean hasRemovedAny(MapItemSavedData instance, @Local(name = "decoration") MapDecoration decoration) {
        return decoration != null;
    }

    //Start of: Avoid removing player holders if they would be recreated immediately, causing excess packets.
    // Not removing player holder -> send update packets when map content changes
    // Removing player holder -> holder recreated -> send the entire map data

    @WrapOperation(
            method = "tickCarriedBy",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;")
    )
    private <V> V removeIfCannotSeeMap(Map<Player, MapItemSavedData.HoldingPlayer> instance, Object o, Operation<V> original) {
        if (o instanceof Player player && instance.get(player) instanceof MapObservingPlayer mapObservingPlayer && mapObservingPlayer.lithium$requiresMapUpdates()) {
            return null;
        } else {
            return original.call(instance, o);
        }
    }
    @WrapOperation(
            method = "tickCarriedBy",
            at = @At(value = "INVOKE", target = "Ljava/util/List;remove(Ljava/lang/Object;)Z")
    )
    private boolean removeIfCannotSeeMap(List<?> instance, Object o, Operation<Boolean> original) {
        if (o instanceof MapObservingPlayer mapObservingPlayer && mapObservingPlayer.lithium$requiresMapUpdates()) {
            return false;
        } else {
            return original.call(instance, o);
        }
    }
    //End of: Avoid removing player holders if they would be recreated immediately, causing excess packets


    //Start of: Keep custom fields up to date
    @WrapOperation(
            method = "tickCarriedBy",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;contains(Ljava/util/function/Predicate;)Z"
            )
    )
    private boolean contains(Inventory inventory, Predicate<ItemStack> predicate, Operation<Boolean> original) {
        boolean inventoryContains = original.call(inventory, predicate);
        if (this.carriedByPlayers.get(inventory.player) instanceof MapObservingPlayer mapObservingPlayer) {
            mapObservingPlayer.lithium$setHasMapInInventory(inventoryContains);
        }
        return inventoryContains;
    }

    @Inject(
            method = "tickCarriedBy",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;mapMatcher(Lnet/minecraft/world/item/ItemStack;)Ljava/util/function/Predicate;")
    )
    private void setPlayerCanSeeFramedMap(Player tickingPlayer, ItemStack itemStack, ItemFrame placedInFrame, CallbackInfo ci) {
        if (placedInFrame != null && this.carriedByPlayers.get(tickingPlayer) instanceof MapObservingPlayer mapObservingPlayer) {
            mapObservingPlayer.lithium$setCanSeeMapInItemFrame();
        }
    }
    //End of: Keep custom fields up to date

    //Start of: Replace quadratic player scanning with a single player scan
    @ModifyReceiver(
            method = "tickCarriedBy(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/decoration/ItemFrame;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;size()I")
    )
    private List<MapItemSavedData.HoldingPlayer> avoidQuadraticPlayerUpdating(List<MapItemSavedData.HoldingPlayer> instance, @Local(argsOnly = true, name = "placedInFrame") ItemFrame placedInFrame, @Share("itemFrameIsUpdatingAllPlayers") LocalBooleanRef itemFrameIsUpdatingAllPlayers) {
        if (placedInFrame != null && !itemFrameIsUpdatingAllPlayers.get() && placedInFrame.level() instanceof ServerLevel serverLevel) {
            //Item frames do not need to update all players several times, once per tick is enough.
            if (this.lastUpdatedAllPlayers == (serverLevel.getServer().getTickCount())) {
                //noinspection unchecked
                return Collections.EMPTY_LIST; //Skip the entire loop when already updated all players in the previous call or even from a different item frame entity
            }
            this.lastUpdatedAllPlayers = serverLevel.getServer().getTickCount();
            itemFrameIsUpdatingAllPlayers.set(true);
        }
        return instance;
    }

    @ModifyArg(
            method = "tickCarriedBy(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/decoration/ItemFrame;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;equals(Ljava/lang/Object;)Z")
    )
    private Object getPlayerOrNullIfUpdatingMultiplePlayers(Object player, @Share("itemFrameIsUpdatingAllPlayers") LocalBooleanRef itemFrameIsUpdatingAllPlayers) {
        //In case the above mixin set itemFrameIsUpdatingAllPlayers, we do not want to exclude the currently ticking player from decoration removal when they are invisible.
        // Unless they are the only player
        if (itemFrameIsUpdatingAllPlayers.get() && this.carriedBy.size() > 1) {
            return null;
            //Equality will return false -> all player decorations with invisibility will be removed.

            // This is different from vanilla with modded clients that display player decorations on item frames.
            // In vanilla the shown decorations depends on the player order in the list, the first player may see his own decoration even if he is hidden.
            // With lithium, no player, even the first one will see his own decoration when he is hidden.
            // Note that this only affects item frame packets.
        }
        return player;
    }
}
