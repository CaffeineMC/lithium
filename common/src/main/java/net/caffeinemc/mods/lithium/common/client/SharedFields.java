package net.caffeinemc.mods.lithium.common.client;

import net.caffeinemc.mods.lithium.common.ai.brain.memories.BrainExtended;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemorySlot;

import java.util.concurrent.atomic.AtomicInteger;

public class SharedFields {
    public static final AtomicInteger MAXIMUM_BIOME_PARTICLE_CHANCE = new AtomicInteger(Float.floatToIntBits(0.0F)); //Using atomic integer as replacement for atomic float

    //mixin.experimental.client_tick.entity.unused_brain must be enabled for the following fields
    public static final MemorySlot<?> DUMMY_SLOT;
    public static final Brain<?> DUMMY_BRAIN;

    static {

        if (BrainExtended.class.isAssignableFrom(Brain.class)) {
            DUMMY_SLOT = MemorySlot.create();
            var brain = new Brain<>();
            ((BrainExtended) brain).lithium$pretendAllMemoryTypesRegistered();
            DUMMY_BRAIN = brain;
        } else {
            DUMMY_SLOT = null;
            DUMMY_BRAIN = null;
        }
    }
}
