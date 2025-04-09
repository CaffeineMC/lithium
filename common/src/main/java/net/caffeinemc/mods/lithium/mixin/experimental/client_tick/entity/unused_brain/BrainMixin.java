package net.caffeinemc.mods.lithium.mixin.experimental.client_tick.entity.unused_brain;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.AbstractReference2ObjectFunction;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.caffeinemc.mods.lithium.common.util.collections.DummyList;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Mixin(value = Brain.class, priority = 1010)
//Apply after collections.brain.BrainMixin, which replaces the brain collections, deleting the default return value
public class BrainMixin {

    @Mutable
    @Shadow
    @Final
    private Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories;


    @Inject(
            method = "<init>(Ljava/util/Collection;Ljava/util/Collection;Lcom/google/common/collect/ImmutableList;Ljava/util/function/Supplier;)V",
            at = @At("RETURN")
    )
    private void pretendMemoryTypeRegisteredInDummyBrain(Collection<?> memories, Collection<?> sensors, ImmutableList<?> memoryEntries, Supplier<?> codecSupplier, CallbackInfo ci) {
        if (memories instanceof DummyList<?>) {
            if (this.memories instanceof AbstractReference2ObjectFunction<?, ?> memoryCollection) {
                //noinspection unchecked
                ((AbstractReference2ObjectFunction<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>>) memoryCollection).defaultReturnValue(Optional.empty());
            } else {
                Reference2ObjectOpenHashMap<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memoryCollection = new Reference2ObjectOpenHashMap<>(this.memories);
                memoryCollection.defaultReturnValue(Optional.empty());
                this.memories = memoryCollection;
            }

        }
    }

}
