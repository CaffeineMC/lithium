package net.caffeinemc.mods.lithium.mixin.client_tick.entity.unused_brain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.lithium.common.client.SharedFields;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @WrapOperation(
            method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;makeBrain(Lnet/minecraft/world/entity/ai/Brain$Packed;)Lnet/minecraft/world/entity/ai/Brain;")
    )
    private Brain<?> doNotCreateClientBrain(LivingEntity instance, Brain.Packed packed, Operation<Brain<?>> original) {
        if (instance.level().isClientSide()) {
            return SharedFields.DUMMY_BRAIN;
        }
        return original.call(instance, packed);
    }
}
