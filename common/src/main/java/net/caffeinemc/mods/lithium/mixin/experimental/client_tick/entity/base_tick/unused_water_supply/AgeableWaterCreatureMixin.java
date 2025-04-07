package net.caffeinemc.mods.lithium.mixin.experimental.client_tick.entity.base_tick.unused_water_supply;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AgeableWaterCreature;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AgeableWaterCreature.class)
public abstract class AgeableWaterCreatureMixin extends AgeableMob {

    protected AgeableWaterCreatureMixin(EntityType<? extends AgeableMob> entityType, Level level) {
        super(entityType, level);
    }

    @WrapWithCondition(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/AgeableWaterCreature;handleAirSupply(I)V"))
    private boolean isServerSide(AgeableWaterCreature creature, int i) {
        return !creature.level().isClientSide();
    }
}
