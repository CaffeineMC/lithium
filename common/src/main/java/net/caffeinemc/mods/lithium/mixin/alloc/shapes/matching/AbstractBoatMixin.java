package net.caffeinemc.mods.lithium.mixin.alloc.shapes.matching;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.lithium.common.shapes.offset_operations.LithiumOffsetShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractBoat.class)
public class AbstractBoatMixin {

    @Redirect(
            method = "getGroundFriction()F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/VoxelShape;move(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/shapes/VoxelShape;")
    )
    private VoxelShape noMove(VoxelShape instance, Vec3i delta) {
        return instance;
    }

    @Redirect(
            method = "getGroundFriction()F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z")
    )
    private boolean useNonAllocating(VoxelShape blockCollisionShape, VoxelShape boatShape, BooleanOp op, @Local(name = "blockPos") BlockPos.MutableBlockPos blockPos, @Local(name = "box") AABB box) {
        return LithiumOffsetShapes.joinIsNotEmpty(blockCollisionShape, blockPos.getX(), blockPos.getY(), blockPos.getZ(), box, boatShape, op);
    }
}
