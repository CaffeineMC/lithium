package net.caffeinemc.mods.lithium.mixin.experimental.client_tick.entity.unused_brain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PathfinderMob.class)
public class PathfinderMobMixin extends Mob {

    protected PathfinderMobMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @WrapOperation(
            method = "isPanicking()Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;hasMemoryValue(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;)Z")
    )
    private boolean hasMemoryValue(Brain<?> instance, MemoryModuleType<?> memoryModuleType, Operation<Boolean> original) {
        return instance != null && original.call(instance, memoryModuleType);
    }
}
