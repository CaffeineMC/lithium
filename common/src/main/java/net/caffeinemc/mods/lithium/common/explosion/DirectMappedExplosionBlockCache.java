package net.caffeinemc.mods.lithium.common.explosion;

import net.minecraft.world.level.block.state.BlockState;

public record DirectMappedExplosionBlockCache(long[] directMappedTags, BlockState[] directMappedStates,
                                              float[] directMappedResistances) {
    public DirectMappedExplosionBlockCache(int size) {
        this(new long[size], new BlockState[size], new float[size]);
    }
}
