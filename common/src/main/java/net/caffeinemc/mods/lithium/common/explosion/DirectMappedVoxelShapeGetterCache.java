package net.caffeinemc.mods.lithium.common.explosion;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;

public record DirectMappedVoxelShapeGetterCache(long[] directMappedTags, VoxelShape[] directMappedStates) {
    private static final int DIRECT_CACHE_BITS = 8;
    private static final int DIRECT_CACHE_SIZE = 1 << DIRECT_CACHE_BITS;
    private static final int DIRECT_CACHE_MASK = (1 << DIRECT_CACHE_BITS) - 1;

    public static final ThreadLocal<DirectMappedVoxelShapeGetterCache> BLOCK_CACHE_TL = ThreadLocal.withInitial(() -> new DirectMappedVoxelShapeGetterCache(DIRECT_CACHE_SIZE));

    public DirectMappedVoxelShapeGetterCache(int size) {
        this(new long[size], new VoxelShape[size]);
    }

    public VoxelShape getCollisionShape(long posLong, Level level, CollisionContext context) {
        //No need to check for the MIN_VALUE sentinel here, since performRayCast prevents MIN_VALUE / the equivalent
        // block position from reaching this method as parameter.
        int index = posToCacheIndex(posLong);
        if (this.directMappedTags[index] == posLong) {
            return this.directMappedStates[index];
        } else {
            return null;
        }
    }

    public void cacheEntry(VoxelShape collisionShape, long posLong) {
        int index = posToCacheIndex(posLong);
        this.directMappedTags[index] = posLong;
        this.directMappedStates[index] = collisionShape;
    }

    private static int posToCacheIndex(long posLong) {
        return ((int) HashCommon.mix(posLong)) & DIRECT_CACHE_MASK;
    }


    public void invalidate() {
        Arrays.fill(this.directMappedTags, Long.MIN_VALUE);
    }
}
