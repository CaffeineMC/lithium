package net.caffeinemc.mods.lithium.mixin.entity.collisions.movement;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.caffeinemc.mods.lithium.common.entity.LithiumEntityCollisions;
import net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeperVoxelShape;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    public abstract Level level();

    @Shadow
    public abstract AABB getBoundingBox();

    @Redirect(
            method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private List<VoxelShape> postponeGetEntities(Level world, Entity entity, AABB box, @Share("requireAddEntities") LocalBooleanRef requireAddEntities) {
        requireAddEntities.set(true);
        return new ArrayList<>();
    }


    @Redirect(
            method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;collideBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 collideMovementWithPostponedGetEntities(@Nullable Entity entity, Vec3 movement, AABB entityBoundingBox, Level world, List<VoxelShape> entityCollisions, @Share("requireAddEntities") LocalBooleanRef requireAddEntities) {
        return lithium$CollideMovement(entity, movement, entityBoundingBox, world, entityCollisions, requireAddEntities);
    }

    @ModifyVariable(
            method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(
                    value = "INVOKE", shift = At.Shift.BEFORE,
                    target = "Lnet/minecraft/world/entity/Entity;collectCollidersIgnoringWorldBorder(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Ljava/util/List;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;")
    )
    private List<VoxelShape> collectEntities(List<VoxelShape> entityCollisions, @Share("requireAddEntities") LocalBooleanRef requireAddEntities) {
        if (requireAddEntities.get()) {
            requireAddEntities.set(false);
            ArrayList<VoxelShape> collisions = entityCollisions instanceof ArrayList<VoxelShape> ? (ArrayList<VoxelShape>) entityCollisions : new ArrayList<>(entityCollisions);
            LithiumEntityCollisions.appendEntityCollisions(collisions, this.level(), (Entity) (Object) this, this.getBoundingBox());
            return collisions;
        }
        return entityCollisions;
    }

    /**
     * @author 2No2Name
     * @reason Replace with optimized implementation
     */
    @Overwrite
    public static Vec3 collideBoundingBox(@Nullable Entity entity, Vec3 movement, AABB entityBoundingBox, Level world, List<VoxelShape> entityCollisions) {
        return lithium$CollideMovement(entity, movement, entityBoundingBox, world, entityCollisions, null);
    }
    @Unique
    private static Vec3 lithium$CollideMovement(@Nullable Entity entity, Vec3 movement, AABB entityBoundingBox, Level world, List<VoxelShape> entityCollisions, LocalBooleanRef requireAddEntities) {
        //vanilla order: entities, world border, blocks.
        // The most important ordering constraint is that the last collision is last, since the result is not clipped to 0 when it is <1e-7.
        // Other reordering of collisions does not seem to matter.
        double movementX = movement.x;
        double movementY = movement.y;
        double movementZ = movement.z;
        boolean isSingleAxisMovement = (movementX == 0D ? 0 : 1) + (movementY == 0D ? 0 : 1) + (movementZ == 0D ? 0 : 1) == 1;

        if (movementY < 0D) {
            //Downwards / gravity optimization: Check supporting block or directly below center of entity first
            VoxelShape voxelShape = LithiumEntityCollisions.getSupportingCollisionForEntity(world, entity, entityBoundingBox);
            if (voxelShape != null) {
                double v = voxelShape.collide(Direction.Axis.Y, entityBoundingBox, movementY);
                if (v == 0) {
                    if (isSingleAxisMovement) {
                        //Y was the only movement axis, movement completely cancelled<
                        return Vec3.ZERO;
                    }
                    movementY = 0D;
                    isSingleAxisMovement = (movementX == 0D ? 0 : 1) + (movementZ == 0D ? 0 : 1) == 1;
                }
            }
        }

        AABB movementSpace;
        if (isSingleAxisMovement) {
            movementSpace = LithiumEntityCollisions.getSmallerBoxForSingleAxisMovement(movement, entityBoundingBox, movementY, movementX, movementZ);
        } else {
            movementSpace = entityBoundingBox.expandTowards(movement);
        }

        boolean shouldAddEntities = requireAddEntities != null && requireAddEntities.get();
        boolean shouldAddWorldBorder = true;
        // For 1-e7 margin behavior correctness, the last block collision must be last of all collisions
        boolean shouldAddLastBlock = true;
        ChunkAwareBlockCollisionSweeperVoxelShape blockCollisionSweeper = new ChunkAwareBlockCollisionSweeperVoxelShape(world, entity, movementSpace, true, !isSingleAxisMovement);

        ArrayList<VoxelShape> worldBorderAndLastBlockCollision = new ArrayList<>(2);

        if (movementY != 0.0) {
            movementY = Shapes.collide(Direction.Axis.Y, entityBoundingBox, blockCollisionSweeper, movementY);
            if (movementY != 0.0) {
                shouldAddEntities = LithiumEntityCollisions.addEntityCollisionsIfRequired(shouldAddEntities, entity, world, entityCollisions, movementSpace);
                //noinspection ConstantValue
                shouldAddWorldBorder = LithiumEntityCollisions.addWorldBorderCollisionIfRequired(shouldAddWorldBorder, entity, worldBorderAndLastBlockCollision, movementSpace);
                //noinspection ConstantValue
                shouldAddLastBlock = LithiumEntityCollisions.addLastBlockCollisionIfRequired(shouldAddLastBlock, blockCollisionSweeper, worldBorderAndLastBlockCollision);
                if (!entityCollisions.isEmpty()) {
                    movementY = Shapes.collide(Direction.Axis.Y, entityBoundingBox, entityCollisions, movementY);
                }
                if (!worldBorderAndLastBlockCollision.isEmpty()) {
                    movementY = Shapes.collide(Direction.Axis.Y, entityBoundingBox, worldBorderAndLastBlockCollision, movementY);
                }
                
                if (movementY != 0.0) {
                    entityBoundingBox = entityBoundingBox.move(0.0, movementY, 0.0);
                }
            }
        }
        boolean zMovementBiggerThanXMovement = Math.abs(movementX) < Math.abs(movementZ);
        if (zMovementBiggerThanXMovement) {
            movementZ = Shapes.collide(Direction.Axis.Z, entityBoundingBox, blockCollisionSweeper, movementZ);
            if (movementZ != 0.0) {
                //noinspection DuplicatedCode
                shouldAddEntities = LithiumEntityCollisions.addEntityCollisionsIfRequired(shouldAddEntities, entity, world, entityCollisions, movementSpace);
                shouldAddWorldBorder = LithiumEntityCollisions.addWorldBorderCollisionIfRequired(shouldAddWorldBorder, entity, worldBorderAndLastBlockCollision, movementSpace);
                shouldAddLastBlock = LithiumEntityCollisions.addLastBlockCollisionIfRequired(shouldAddLastBlock, blockCollisionSweeper, worldBorderAndLastBlockCollision);
                if (!entityCollisions.isEmpty()) {
                    movementZ = Shapes.collide(Direction.Axis.Z, entityBoundingBox, entityCollisions, movementZ);
                }
                if (!worldBorderAndLastBlockCollision.isEmpty()) {
                    movementZ = Shapes.collide(Direction.Axis.Z, entityBoundingBox, worldBorderAndLastBlockCollision, movementZ);
                }

                if (movementZ != 0.0) {
                    entityBoundingBox = entityBoundingBox.move(0.0, 0.0, movementZ);
                }
            }
        }
        if (movementX != 0.0) {
            movementX = Shapes.collide(Direction.Axis.X, entityBoundingBox, blockCollisionSweeper, movementX);
            if (movementX != 0.0) {
                shouldAddEntities = LithiumEntityCollisions.addEntityCollisionsIfRequired(shouldAddEntities, entity, world, entityCollisions, movementSpace);
                shouldAddWorldBorder = LithiumEntityCollisions.addWorldBorderCollisionIfRequired(shouldAddWorldBorder, entity, worldBorderAndLastBlockCollision, movementSpace);
                shouldAddLastBlock = LithiumEntityCollisions.addLastBlockCollisionIfRequired(shouldAddLastBlock, blockCollisionSweeper, worldBorderAndLastBlockCollision);
                if (!entityCollisions.isEmpty()) {
                    movementX = Shapes.collide(Direction.Axis.X, entityBoundingBox, entityCollisions, movementX);
                }
                if (!worldBorderAndLastBlockCollision.isEmpty()) {
                    movementX = Shapes.collide(Direction.Axis.X, entityBoundingBox, worldBorderAndLastBlockCollision, movementX);
                }

                if (movementX != 0.0) {
                    entityBoundingBox = entityBoundingBox.move(movementX, 0.0, 0.0);
                }
            }
        }
        if (!zMovementBiggerThanXMovement && movementZ != 0.0) {
            movementZ = Shapes.collide(Direction.Axis.Z, entityBoundingBox, blockCollisionSweeper, movementZ);
            if (movementZ != 0.0) {
                //noinspection DuplicatedCode
                shouldAddEntities = LithiumEntityCollisions.addEntityCollisionsIfRequired(shouldAddEntities, entity, world, entityCollisions, movementSpace);
                //noinspection UnusedAssignment
                shouldAddWorldBorder = LithiumEntityCollisions.addWorldBorderCollisionIfRequired(shouldAddWorldBorder, entity, worldBorderAndLastBlockCollision, movementSpace);
                //noinspection UnusedAssignment
                shouldAddLastBlock = LithiumEntityCollisions.addLastBlockCollisionIfRequired(shouldAddLastBlock, blockCollisionSweeper, worldBorderAndLastBlockCollision);
                if (!entityCollisions.isEmpty()) {
                    movementZ = Shapes.collide(Direction.Axis.Z, entityBoundingBox, entityCollisions, movementZ);
                }
                if (!worldBorderAndLastBlockCollision.isEmpty()) {
                    movementZ = Shapes.collide(Direction.Axis.Z, entityBoundingBox, worldBorderAndLastBlockCollision, movementZ);
                }
                //No need to offset box here, as it is the last axis
            }
        }

        if (requireAddEntities != null && !shouldAddEntities) {
            requireAddEntities.set(false);
        }
        return new Vec3(movementX, movementY, movementZ);
    }
}
