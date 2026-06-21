package net.caffeinemc.mods.lithium.mixin.shapes.consistency;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.SliceShape;
import net.minecraft.world.phys.shapes.SubShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SliceShape.class)
public abstract class SliceShapeMixin extends VoxelShape {

    protected SliceShapeMixin(DiscreteVoxelShape shape) {
        super(shape);
    }

    @Shadow
    public abstract DoubleList getCoords(Direction.Axis axis);

    /**
     * Vanilla has multiple issues with SubShape:
     * The start and end indices are assumed to not be an overestimate, but exact everywhere in the code. Vanilla
     * doesn't check this and thus allows SubShape to appear larger than it is to some function calls.
     * Vanilla sometimes creates SubShapes with negative start indices.
     * <p>
     * This mixin fixes both issues.
     */
    @WrapOperation(
            method = "makeSlice", at = @At(value = "NEW", target = "(Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;IIIIII)Lnet/minecraft/world/phys/shapes/SubShape;")
    )
    private static SubShape initSubShapeCorrectly(DiscreteVoxelShape parent, int startX, int startY, int startZ, int endX, int endY, int endZ, Operation<SubShape> original) {
        boolean isEmpty = true;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int x = startX; x < endX && x < parent.getXSize(); x++) {
            for (int y = startY; y < endY && y < parent.getYSize(); y++) {
                for (int z = startZ; z < endZ && z < parent.getZSize(); z++) {
                    if (parent.isFullWide(x, y, z)) {
                        isEmpty = false;
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x + 1);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y + 1);
                        minZ = Math.min(minZ, z);
                        maxZ = Math.max(maxZ, z + 1);
                    }
                }
            }
        }

        if (isEmpty) {
            startX = 0;
            endX = 0;
            startY = 0;
            endY = 0;
            startZ = 0;
            endZ = 0;
        } else {
            startX = minX;
            startY = minY;
            startZ = minZ;
            endX = maxX;
            endY = maxY;
            endZ = maxZ;
        }

        return original.call(parent, startX, startY, startZ, endX, endY, endZ);
    }
}
