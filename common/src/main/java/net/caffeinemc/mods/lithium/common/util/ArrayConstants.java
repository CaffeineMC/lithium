package net.caffeinemc.mods.lithium.common.util;

import net.minecraft.world.phys.AABB;

/**
 * Pre-initialized constants to avoid unnecessary allocations.
 */
public final class ArrayConstants {
    private ArrayConstants() {}

    public static final int[] EMPTY = new int[0];
    public static final AABB[] EMPTY_AABBS = new AABB[0];
    public static final int[] ZERO = new int[]{0};
}
