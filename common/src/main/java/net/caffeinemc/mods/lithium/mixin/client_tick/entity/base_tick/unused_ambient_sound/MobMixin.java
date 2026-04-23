package net.caffeinemc.mods.lithium.mixin.client_tick.entity.base_tick.unused_ambient_sound;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mob.class)
public abstract class MobMixin extends LivingEntity {
    protected MobMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @ModifyExpressionValue(
            method = "baseTick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;isAlive()Z")
    )
    private boolean andIsServerSide(boolean original) {
        return original && (
                !this.level().isClientSide() ||
                        //Breeze can play client side sounds, when not on ground AND client side brain doesn't have a target (it should never have a target client side?)
                        (Object) this instanceof Breeze)
                ;
    }
}
