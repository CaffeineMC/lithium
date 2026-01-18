package net.caffeinemc.mods.lithium.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

public class Distances {

    public static double getMinChunkToBlockDistanceL2Sq(BlockPos origin, int chunkX, int chunkZ) {
        int chunkMinX = SectionPos.sectionToBlockCoord(chunkX);
        int chunkMinZ = SectionPos.sectionToBlockCoord(chunkZ);

        int xDistance = origin.getX() - chunkMinX;
        if (xDistance > 0) {
            xDistance = Math.max(0, xDistance - 15);
        }
        int zDistance = origin.getZ() - chunkMinZ;
        if (zDistance > 0) {
            zDistance = Math.max(0, zDistance - 15);
        }

        return xDistance * xDistance + zDistance * zDistance;
    }

    public static boolean isWithinSquareRadius(BlockPos origin, int radius, BlockPos pos) {
        return Math.abs(pos.getX() - origin.getX()) <= radius &&
                Math.abs(pos.getZ() - origin.getZ()) <= radius;
    }

    public static boolean isWithinCircleRadius(BlockPos origin, double radiusSq, BlockPos pos) {
        return origin.distSqr(pos) <= radiusSq;
    }

    public static int getClosestAlongSectionAxis(int originAxis, int chunkAxis){
        final int chunkMinAxis = SectionPos.sectionToBlockCoord(chunkAxis);
        return Math.min(Math.max(originAxis, chunkMinAxis), chunkMinAxis+15);
    }

    public static long getClosestPositionWithinChunk(BlockPos origin, int chunkX, int chunkZ){
        return BlockPos.asLong(getClosestAlongSectionAxis(origin.getX(), chunkX),
                origin.getY(), getClosestAlongSectionAxis(origin.getZ(), chunkZ));

    }
}
