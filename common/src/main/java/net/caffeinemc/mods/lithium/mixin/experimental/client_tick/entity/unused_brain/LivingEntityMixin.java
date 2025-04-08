package net.caffeinemc.mods.lithium.mixin.experimental.client_tick.entity.unused_brain;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Dynamic;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    private static final Brain<?> DUMMY_BRAIN = new Brain<>(List.of(), List.of(), ImmutableList.of(), () -> {
        throw new IllegalStateException("Trying to serialize client side brain! If you really want this, disable lithium's client side brain optimization!");
    });

    @WrapOperation(
            method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;makeBrain(Lcom/mojang/serialization/Dynamic;)Lnet/minecraft/world/entity/ai/Brain;")
    )
    private Brain<?> doNotCreateClientBrain(LivingEntity instance, Dynamic<?> dynamic, Operation<Brain<?>> original) {
        if (instance.level().isClientSide()) {
            return DUMMY_BRAIN;
        }
        return original.call(instance, dynamic);
    }

    @WrapOperation(
            method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;clearMemories()V")
    )
    private void clearMemoriesIfNotNull(Brain<?> instance, Operation<Void> original) {
        if (instance != null) {
            original.call(instance);
        }
    }
}
