package net.caffeinemc.mods.lithium.common.explosion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ExplosionEntityRays {

    static {
        //noinspection ConstantValue
        if (Direction.Axis.X.ordinal() != 0) {
            throw new IllegalStateException("Axis ordinals incorrect!");
        }
        //noinspection ConstantValue
        if (Direction.Axis.Y.ordinal() != 1) {
            throw new IllegalStateException("Axis ordinals incorrect!");
        }
        //noinspection ConstantValue
        if (Direction.Axis.Z.ordinal() != 2) {
            throw new IllegalStateException("Axis ordinals incorrect!");
        }
    }

    public static boolean doesRayHitOffsetAABBVolumes(AABB[] voxelShapeAABBs, BlockPos blockPos, final Vec3 from, final Vec3 to) {
        //[VanillaCopy] VoxelShape#clip and callees
        if (voxelShapeAABBs.length != 0) {
            double rayDx = to.x - from.x;
            double rayDy = to.y - from.y;
            double rayDz = to.z - from.z;
            double rayLengthSq = rayDx * rayDx + rayDy * rayDy + rayDz * rayDz;
            if (!(rayLengthSq < 1.0E-7)) {
                //[VanillaCopy] AABB#clip and callees

                for (AABB aabb : voxelShapeAABBs) {
                    if (doesRayHitOffsetAABBVolume(aabb, blockPos.getX(), blockPos.getY(), blockPos.getZ(), from, rayDx, rayDy, rayDz)) {
                        return true;
                    }
                }

            }
        }
        return false;
    }
    //[VanillaCopy] AABB#clip and callees
    private static boolean doesRayHitOffsetAABBVolume(final AABB aabb, int blockX, int blockY, int blockZ, final Vec3 from, final double rayDx, final double rayDy, final double dayDz) {
        //[VanillaCopy] VoxelShape#clip. Needed since only the surface is checked afterward. The 0.001 offset is added because VoxelShape#clip adds it.
        double x2 = from.x + rayDx * 0.001;
        double y2 = from.y + rayDy * 0.001;
        double z2 = from.z + dayDz * 0.001;
        double minX = aabb.minX + blockX;
        double maxX = aabb.maxX + blockX;
        double minY = aabb.minY + blockY;
        double maxY = aabb.maxY + blockY;
        double minZ = aabb.minZ + blockZ;
        double maxZ = aabb.maxZ + blockZ;
        if (
            //VoxelShapes are inclusive on the lower bound and exclusive on the upper bound in the relevant code
                minX <= x2 && x2 < maxX &&
                        minY <= y2 && y2 < maxY &&
                        minZ <= z2 && z2 < maxZ
        ) {
            return true;
        }
        //End of [VanillaCopy] VoxelShape#clip

        if (rayDx > 1.0E-7) {
            if (doesRayHitAABBSurface(rayDx, rayDy, dayDz, minX, minY, maxY, minZ, maxZ, from.x, from.y, from.z)) {
                return true;
            }

        } else if (rayDx < -1.0E-7) {
            if (doesRayHitAABBSurface(rayDx, rayDy, dayDz, maxX, minY, maxY, minZ, maxZ, from.x, from.y, from.z)) {
                return true;
            }

        }

        if (rayDy > 1.0E-7) {
            if (doesRayHitAABBSurface(rayDy, dayDz, rayDx, minY, minZ, maxZ, minX, maxX, from.y, from.z, from.x)) {
                return true;
            }

        } else if (rayDy < -1.0E-7) {
            if (doesRayHitAABBSurface(rayDy, dayDz, rayDx, maxY, minZ, maxZ, minX, maxX, from.y, from.z, from.x)) {
                return true;
            }

        }

        if (dayDz > 1.0E-7) {
            return doesRayHitAABBSurface(dayDz, rayDx, rayDy, minZ, minX, maxX, minY, maxY, from.z, from.x, from.y);
        } else if (dayDz < -1.0E-7) {
            return doesRayHitAABBSurface(dayDz, rayDx, rayDy, maxZ, minX, maxX, minY, maxY, from.z, from.x, from.y);
        }

        return false;
    }

    //[VanillaCopy] AABB#clip and callees
    private static boolean doesRayHitAABBSurface(final double da, final double db, final double dc, final double startA, final double minB, final double maxB, final double minC, final double maxC, final double fromA, final double fromB, final double fromC) {
        double s = (startA - fromA) / da;
        double pb = fromB + s * db;
        double pc = fromC + s * dc;
        return 0.0 < s && s < 1.0 && minB - 1.0E-7 < pb && pb < maxB + 1.0E-7 && minC - 1.0E-7 < pc && pc < maxC + 1.0E-7;
    }
}
