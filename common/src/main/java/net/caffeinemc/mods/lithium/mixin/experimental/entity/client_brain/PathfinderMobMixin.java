package net.caffeinemc.mods.lithium.mixin.experimental.entity.client_brain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PathfinderMob.class)
public class PathfinderMobMixin {

    @WrapOperation(
            method = "isPanicking()Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;hasMemoryValue(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;)Z")
    )
    private boolean hasMemoryValue(Brain<?> instance, MemoryModuleType<?> memoryModuleType, Operation<Boolean> original) {
        return instance != null && original.call(instance, memoryModuleType);
    }
}
