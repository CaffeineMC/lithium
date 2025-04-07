package net.caffeinemc.mods.lithium.mixin.experimental.client_tick.entity.unused_brain;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobMixin extends LivingEntity {
    protected MobMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "getTargetFromBrain", at = @At("HEAD"), cancellable = true)
    private void nullIfNullBrain(CallbackInfoReturnable<LivingEntity> cir) {
        //noinspection ConstantValue
        if (this.getBrain() == null) {
            cir.setReturnValue(null);
        }
    }
}
