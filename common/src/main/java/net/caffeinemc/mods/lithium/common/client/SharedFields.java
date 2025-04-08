package net.caffeinemc.mods.lithium.common.client;

import java.util.concurrent.atomic.AtomicInteger;

public class SharedFields {
    public static final AtomicInteger MAXIMUM_BIOME_PARTICLE_CHANCE = new AtomicInteger(Float.floatToIntBits(0.0F)); //Using atomic integer as replacement for atomic float
}
