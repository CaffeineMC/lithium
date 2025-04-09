package net.caffeinemc.mods.lithium.common.client;

import com.google.common.collect.ImmutableList;
import net.caffeinemc.mods.lithium.common.util.collections.DummyList;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SharedFields {
    public static final AtomicInteger MAXIMUM_BIOME_PARTICLE_CHANCE = new AtomicInteger(Float.floatToIntBits(0.0F)); //Using atomic integer as replacement for atomic float
    @Unique
    public static final Brain<?> DUMMY_BRAIN = new Brain<>(new DummyList<>(), List.of(), ImmutableList.of(), () -> {
        throw new IllegalStateException("Trying to serialize client side brain! If you really want this, disable lithium's client side brain optimization!");
    });
}
