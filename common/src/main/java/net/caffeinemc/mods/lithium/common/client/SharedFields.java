package net.caffeinemc.mods.lithium.common.client;

import net.caffeinemc.mods.lithium.common.ai.brain.memories.BrainExtended;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemorySlot;

import java.util.concurrent.atomic.AtomicInteger;

public class SharedFields {
    public static final AtomicInteger MAXIMUM_BIOME_PARTICLE_CHANCE = new AtomicInteger(Float.floatToIntBits(0.0F)); //Using atomic integer as replacement for atomic float
    public static final MemorySlot<?> DUMMY_SLOT;
    public static final Brain<?> DUMMY_BRAIN;

    static {
        DUMMY_SLOT = MemorySlot.create();

        var brain = new Brain<>();
        ((BrainExtended) brain).lithium$pretendAllMemoryTypesRegistered();
        DUMMY_BRAIN = brain;
    }
}
