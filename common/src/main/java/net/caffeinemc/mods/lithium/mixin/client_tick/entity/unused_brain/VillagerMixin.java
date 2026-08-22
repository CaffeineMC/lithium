package net.caffeinemc.mods.lithium.mixin.client_tick.entity.unused_brain;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Villager.class)
public abstract class VillagerMixin extends Entity {

    public VillagerMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    /**
     * Vanilla writes the LAST_WOKEN memory on both sides: when a sleeping
     * villager dies, the client's death entity event runs
     * {@code LivingEntity.die -> Villager.stopSleeping -> Brain.setMemory},
     * which trips the dummy-brain guard inside packet handling and
     * disconnects the client.
     */
    @WrapWithCondition(
            method = "stopSleeping", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;setMemory(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;)V")
    )
    private <U> boolean isServerSide(Brain instance, MemoryModuleType<U> type, @Nullable U value) {
        return !this.level().isClientSide();
    }
}
