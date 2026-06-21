package net.caffeinemc.mods.lithium.mixin.alloc.shapes.matching;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.lithium.common.shapes.offset_operations.LithiumOffsetShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    public abstract AABB getBoundingBox();

    @Redirect(
            method = "isColliding(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/VoxelShape;move(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/shapes/VoxelShape;")
    )
    private VoxelShape noMove(VoxelShape instance, Vec3i delta) {
        return instance;
    }

    @Redirect(
            method = "isColliding(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;create(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/shapes/VoxelShape;")
    )
    private VoxelShape noCreate(AABB aabb) {
        return null;
    }

    @Redirect(
            method = "isColliding(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z")
    )
    private boolean useNonAllocating(VoxelShape blockCollisionShape, VoxelShape second, BooleanOp op, @Local(argsOnly = true) BlockPos pos) {
        return LithiumOffsetShapes.joinIsNotEmpty(blockCollisionShape, pos.getX(), pos.getY(), pos.getZ(), this.getBoundingBox(), second, op);
    }

    @Redirect(
            method = "lambda$isInWall$0(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/core/BlockPos;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/VoxelShape;move(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/shapes/VoxelShape;")
    )
    private VoxelShape noMove2(VoxelShape instance, Vec3i delta) {
        return instance;
    }

    @Redirect(
            method = "lambda$isInWall$0(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/core/BlockPos;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;create(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/shapes/VoxelShape;")
    )
    private VoxelShape noCreate2(AABB aabb) {
        return null;
    }

    @Redirect(
            method = "lambda$isInWall$0(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/core/BlockPos;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z")
    )
    private boolean useNonAllocating2(VoxelShape blockCollisionShape, VoxelShape second, BooleanOp op, @Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) AABB eyeBb) {
        return LithiumOffsetShapes.joinIsNotEmpty(blockCollisionShape, pos.getX(), pos.getY(), pos.getZ(), eyeBb, second, op);
    }
}
