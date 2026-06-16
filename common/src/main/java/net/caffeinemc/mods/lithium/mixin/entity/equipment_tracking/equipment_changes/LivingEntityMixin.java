package net.caffeinemc.mods.lithium.mixin.entity.equipment_tracking.equipment_changes;

import net.caffeinemc.mods.lithium.common.entity.EquipmentInfo;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    @Shadow
    @Final
    protected EntityEquipment equipment;

    public LivingEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Inject(
            method = "collectEquipmentChanges(Ljava/util/Map;)Ljava/util/Map;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void skipSentEquipmentComparison(CallbackInfoReturnable<@Nullable Map<EquipmentSlot, ItemStack>> cir) {
        if (!((EquipmentInfo) this.equipment).lithium$hasUnsentEquipmentChanges()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(
            method = "detectEquipmentUpdates()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;handleHandSwap(Ljava/util/Map;)V"
            )
    )
    private void resetEquipmentChanged(CallbackInfo ci) {
        //Not implemented for player entities.
        //noinspection ConstantValue
        if (!((Object) this instanceof Player)) {
            ((EquipmentInfo) this.equipment).lithium$onEquipmentChangesSent();
        }
    }
}
