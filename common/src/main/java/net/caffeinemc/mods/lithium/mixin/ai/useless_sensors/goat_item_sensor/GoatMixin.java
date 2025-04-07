package net.caffeinemc.mods.lithium.mixin.ai.useless_sensors.goat_item_sensor;

import net.caffeinemc.mods.lithium.common.ai.brain.SensorHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Goat.class)
public abstract class GoatMixin extends LivingEntity {

    protected GoatMixin(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Shadow
    public abstract Brain<Goat> getBrain();

    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void disableItemSensor(CallbackInfo ci) {
        if (this.level().isClientSide()) {
            return;
        }
        //NEAREST_VISIBLE_WANTED_ITEM is not used and is not save-able. Therefore, not creating it saves a bit of lag.
        SensorHelper.disableSensor(this, SensorType.NEAREST_ITEMS);
    }
}
