package net.caffeinemc.mods.lithium.mixin.alloc.entity_movement_collisions;

import net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeperVoxelShape;
import net.caffeinemc.mods.lithium.common.shapes.offset_operations.LithiumOffsetCollide;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Shapes.class)
public class ShapesMixin {

    /**
     * @author 2No2Name
     * @reason remove VoxelShape allocations
     */
    @Inject(
            method = "collide(Lnet/minecraft/core/Direction$Axis;Lnet/minecraft/world/phys/AABB;Ljava/lang/Iterable;D)D",
            at = @At("HEAD"), cancellable = true
    )
    private static void collide(Direction.Axis axis, AABB moving, Iterable<VoxelShape> shapes, double distance, CallbackInfoReturnable<Double> cir) {
        if (shapes instanceof ChunkAwareBlockCollisionSweeperVoxelShape collisionSweeper) {
            collisionSweeper = collisionSweeper.iterator();
            while (collisionSweeper.hasNext()) {
                if (Math.abs(distance) < 1.0E-7) {
                    cir.setReturnValue(0.0);
                    return;
                }
                VoxelShape shape = collisionSweeper.nextWithoutOffset();
                int offsetX = collisionSweeper.currentOffsetX();
                int offsetY = collisionSweeper.currentOffsetY();
                int offsetZ = collisionSweeper.currentOffsetZ();

                distance = LithiumOffsetCollide.collide(shape, offsetX, offsetY, offsetZ, axis, moving, distance);
            }
            cir.setReturnValue(distance);
        }
    }
}
