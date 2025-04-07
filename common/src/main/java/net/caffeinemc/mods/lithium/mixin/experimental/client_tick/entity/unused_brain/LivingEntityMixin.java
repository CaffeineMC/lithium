package net.caffeinemc.mods.lithium.mixin.experimental.client_tick.entity.unused_brain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Dynamic;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @WrapOperation(
            method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;makeBrain(Lcom/mojang/serialization/Dynamic;)Lnet/minecraft/world/entity/ai/Brain;")
    )
    private Brain<?> doNotCreateClientBrain(LivingEntity instance, Dynamic<?> dynamic, Operation<Brain<?>> original) {
        if (instance.level().isClientSide()) {
            return null;
        }
        return original.call(instance, dynamic);
    }
}
