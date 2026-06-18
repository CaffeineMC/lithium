package net.caffeinemc.mods.lithium.mixin.ai.useless_sensors.baby_specific_sensors;

import net.caffeinemc.mods.lithium.common.ai.brain.SensorHelper;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(AgeableMob.class)
public abstract class AgeableMobMixin extends LivingEntity {

    @Shadow
    public abstract boolean isBaby();

    protected AgeableMobMixin(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }


    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void disableItemSensor(CallbackInfo ci) {
        this.disableOrEnableParentSensor();
    }

    @Inject(
            method = "onSyncedDataUpdated",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/AgeableMob;refreshDimensions()V"),
            require = 1, allow = 1
    )
    private void handleParentSensor(EntityDataAccessor<?> data, CallbackInfo ci) {
        this.disableOrEnableParentSensor();
    }

    @Unique
    private void disableOrEnableParentSensor() {
        if (this.level().isClientSide()) {
            return;
        }
        if (isBaby()) {
            SensorHelper.enableSensor((AgeableMob) (Object) this, SensorType.NEAREST_ADULT, true);
            if ((Object) this instanceof HappyGhast) {
                SensorHelper.enableSensor((AgeableMob) (Object) this, SensorType.NEAREST_ADULT_ANY_TYPE);
            }
            if ((Object) this instanceof Villager) {
                SensorHelper.enableSensor((AgeableMob) (Object) this, SensorType.VILLAGER_BABIES);
            }
        } else {
            SensorHelper.disableSensor((AgeableMob) (Object) this, SensorType.NEAREST_ADULT); //Applies to most brained animals following adult animals
            if ((Object) this instanceof HappyGhast) {
                SensorHelper.disableSensor((AgeableMob) (Object) this, SensorType.NEAREST_ADULT_ANY_TYPE); //Only applies to baby happy ghast, adult happy ghasts use the non-brain goal selector system
            }
            if ((Object) this instanceof Villager) {
                SensorHelper.disableSensor((AgeableMob) (Object) this, SensorType.VILLAGER_BABIES); //Villager play package only applies to villager babies
            }

            if (this.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_ADULT)) {
                this.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT, Optional.empty());
            }
        }
    }
}
