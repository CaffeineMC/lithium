package net.caffeinemc.mods.lithium.mixin.experimental.client_tick.entity.unused_brain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.allay.Allay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Allay.class)
public class AllayMixin {

    @WrapOperation(
            method = "isOnPickupCooldown()Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;checkMemory(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Lnet/minecraft/world/entity/ai/memory/MemoryStatus;)Z")
    )
    private boolean hasMemoryValue(Brain<?> instance, MemoryModuleType<?> memoryModuleType, MemoryStatus memoryStatus, Operation<Boolean> original) {
        if (instance == null) {
            return false;
        }
        return original.call(instance, memoryModuleType, memoryStatus);
    }
}
