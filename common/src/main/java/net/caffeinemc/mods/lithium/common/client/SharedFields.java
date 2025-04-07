package net.caffeinemc.mods.lithium.common.client;

import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.atomic.AtomicInteger;

public class SharedFields {
    @Unique
    public static final AtomicInteger MAXIMUM_BIOME_PARTICLE_CHANCE = new AtomicInteger(Float.floatToIntBits(0.0F)); //Using atomic integer as replacement for atomic float
}
