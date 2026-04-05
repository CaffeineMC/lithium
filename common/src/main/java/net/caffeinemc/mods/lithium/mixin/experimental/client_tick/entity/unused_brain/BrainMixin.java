package net.caffeinemc.mods.lithium.mixin.experimental.client_tick.entity.unused_brain;

import it.unimi.dsi.fastutil.objects.AbstractReference2ObjectFunction;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.caffeinemc.mods.lithium.common.ai.brain.memories.BrainExtended;
import net.caffeinemc.mods.lithium.common.client.SharedFields;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemorySlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(value = Brain.class)
public class BrainMixin implements BrainExtended {

    @Mutable
    @Shadow
    @Final
    private Map<MemoryModuleType<?>, MemorySlot<?>> memories;


    @Override
    public void lithium$pretendAllMemoryTypesRegistered() {
        if (this.memories instanceof AbstractReference2ObjectFunction<?, ?> memoryCollection) {
            //noinspection unchecked
            ((AbstractReference2ObjectFunction<MemoryModuleType<?>, MemorySlot<?>>) memoryCollection).defaultReturnValue(SharedFields.DUMMY_SLOT);
        } else {
            Reference2ObjectOpenHashMap<MemoryModuleType<?>, MemorySlot<?>> memoryCollection = new Reference2ObjectOpenHashMap<>(this.memories);
            memoryCollection.defaultReturnValue(SharedFields.DUMMY_SLOT);
            this.memories = memoryCollection;
        }
    }

}
