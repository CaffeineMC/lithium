package net.caffeinemc.mods.lithium.common.entity;

import com.google.common.collect.AbstractIterator;
import net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper;
import net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeperVoxelShape;
import net.caffeinemc.mods.lithium.common.util.Pos;
import net.caffeinemc.mods.lithium.common.world.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LithiumEntityCollisions {
    public static final double EPSILON = 1.0E-7D;

    /**
     * [VanillaCopy] CollisionView#getBlockCollisions(Entity, Box)
     * This is a much, much faster implementation which uses simple collision testing against full-cube block shapes.
     * Checks against the world border are replaced with our own optimized functions which do not go through the
     * VoxelShape system.
     */
    public static List<VoxelShape> getBlockCollisions(Level world, Entity entity, AABB box) {
        return new ChunkAwareBlockCollisionSweeperVoxelShape(world, entity, box).collectAll();
    }

    /***
     * @return True if the box (possibly that of an entity's) collided with any blocks
     */
    public static boolean doesBoxCollideWithBlocks(Level world, @Nullable Entity entity, AABB box) {
        final ChunkAwareBlockCollisionSweeperVoxelShape sweeper = new ChunkAwareBlockCollisionSweeperVoxelShape(world, entity, box);

        final VoxelShape shape = sweeper.computeNext();

        return shape != null && !shape.isEmpty();
    }

    /**
     * @return True if the box (possibly that of an entity's) collided with any other hard entities
     */
    public static boolean doesBoxCollideWithHardEntities(EntityGetter view, @Nullable Entity entity, AABB box) {
        if (isBoxEmpty(box)) {
            return false;
        }

        return getEntityWorldBorderCollisionIterable(view, entity, box.inflate(EPSILON), false).iterator().hasNext();
    }

    /**
     * Collects entity and world border collision boxes.
     */
    public static void appendEntityCollisions(List<VoxelShape> entityCollisions, Level world, Entity entity, AABB box) {
        if (isBoxEmpty(box)) {
            return;
        }
        AABB expandedBox = box.inflate(EPSILON);

        for(Entity otherEntity : WorldHelper.getEntitiesForCollision(world, expandedBox, entity)) {
            /*
             * {@link Entity#isCollidable()} returns false by default, designed to be overridden by
             * entities whose collisions should be "hard" (boats and shulkers, for now).
             *
             * {@link Entity#collidesWith(Entity)} only allows hard collisions if the calling entity is not riding
             * otherEntity as a vehicle.
             */
            if (entity == null) {
                if (!otherEntity.canBeCollidedWith(null)) {
                    continue;
                }
            } else if (!entity.canCollideWith(otherEntity)) {
                continue;
            }

            entityCollisions.add(Shapes.create(otherEntity.getBoundingBox()));
        }
    }

    public static void appendWorldBorderCollision(ArrayList<VoxelShape> worldBorderCollisions, Entity entity, AABB box) {
        WorldBorder worldBorder = entity.level().getWorldBorder();
        //TODO this might be different regarding 1e-7 margins
        if (!isWithinWorldBorder(worldBorder, box) && isWithinWorldBorder(worldBorder, entity.getBoundingBox())) {
            worldBorderCollisions.add(worldBorder.getCollisionShape());
        }
    }

    /**
     * [VanillaCopy] EntityView#getEntityCollisions
     * Re-implements the function named above without stream code or unnecessary allocations. This can provide a small
     * boost in some situations (such as heavy entity crowding) and reduces the allocation rate significantly.
     */
    public static Iterable<VoxelShape> getEntityWorldBorderCollisionIterable(EntityGetter view, @Nullable Entity entity, AABB box, boolean includeWorldBorder) {
        assert !includeWorldBorder || entity != null;
        return new Iterable<>() {
            private List<Entity> entityList;
            private int nextFilterIndex;

            @NotNull
            @Override
            public Iterator<VoxelShape> iterator() {
                return new AbstractIterator<>() {
                    int index = 0;
                    boolean consumedWorldBorder = false;

                    @Override
                    protected VoxelShape computeNext() {
                        //Initialize list that is shared between multiple iterators as late as possible
                        if (entityList == null) {
                            /*
                             * In case entity's class is overriding Entity#collidesWith(Entity), all types of entities may be (=> are assumed to be) required.
                             * Otherwise, only get entities that override Entity#isCollidable(), as other entities cannot collide.
                             */
                            entityList = WorldHelper.getEntitiesForCollision(view, box, entity);
                            nextFilterIndex = 0;
                        }
                        List<Entity> list = entityList;
                        Entity otherEntity;
                        do {
                            if (this.index >= list.size()) {
                                //get the world border at the end
                                if (includeWorldBorder && !this.consumedWorldBorder) {
                                    this.consumedWorldBorder = true;
                                    WorldBorder worldBorder = entity.level().getWorldBorder();
                                    if (!isWithinWorldBorder(worldBorder, box) && isWithinWorldBorder(worldBorder, entity.getBoundingBox())) {
                                        return worldBorder.getCollisionShape();
                                    }
                                }
                                return this.endOfData();
                            }

                            otherEntity = list.get(this.index);
                            if (this.index >= nextFilterIndex) {
                                /*
                                 * {@link Entity#isCollidable()} returns false by default, designed to be overridden by
                                 * entities whose collisions should be "hard" (boats and shulkers, for now).
                                 *
                                 * {@link Entity#collidesWith(Entity)} only allows hard collisions if the calling entity is not riding
                                 * otherEntity as a vehicle.
                                 */
                                if (entity == null) {
                                    if (!otherEntity.canBeCollidedWith(null)) {
                                        otherEntity = null;
                                    }
                                } else if (!entity.canCollideWith(otherEntity)) {
                                    otherEntity = null;
                                }
                                nextFilterIndex++;
                            }
                            this.index++;
                        } while (otherEntity == null);

                        return Shapes.create(otherEntity.getBoundingBox());
                    }
                };
            }
        };
    }

    /**
     * This provides a faster check for seeing if an entity is within the world border as it avoids going through
     * the slower shape system.
     *
     * @return True if the {@param box} is fully within the {@param border}, otherwise false.
     */
    public static boolean isWithinWorldBorder(WorldBorder border, AABB box) {
        double wboxMinX = Math.floor(border.getMinX());
        double wboxMinZ = Math.floor(border.getMinZ());

        double wboxMaxX = Math.ceil(border.getMaxX());
        double wboxMaxZ = Math.ceil(border.getMaxZ());

        return box.minX >= wboxMinX && box.minX <= wboxMaxX && box.minZ >= wboxMinZ && box.minZ <= wboxMaxZ &&
                box.maxX >= wboxMinX && box.maxX <= wboxMaxX && box.maxZ >= wboxMinZ && box.maxZ <= wboxMaxZ;
    }


    private static boolean isBoxEmpty(AABB box) {
        return box.getSize() <= EPSILON;
    }

    public static boolean doesBoxCollideWithWorldBorder(CollisionGetter collisionView, Entity entity, AABB box) {
        if (isWithinWorldBorder(collisionView.getWorldBorder(), box)) {
            return false;
        } else {
            VoxelShape worldBorderShape = getWorldBorderCollision(collisionView, entity, box);
            return worldBorderShape != null && Shapes.joinIsNotEmpty(worldBorderShape, Shapes.create(box), BooleanOp.AND);
        }
    }

    public static VoxelShape getWorldBorderCollision(CollisionGetter collisionView, @Nullable Entity entity, AABB box) {
        WorldBorder worldBorder = collisionView.getWorldBorder();
        return worldBorder.isInsideCloseToBorder(entity, box) ? worldBorder.getCollisionShape() : null;
    }

    public static @Nullable VoxelShape getSupportingCollisionForEntity(Level world, @Nullable Entity entity, AABB entityBoundingBox, Vec3 movement) {
        if (entity instanceof SupportingBlockCollisionShapeProvider supportingBlockCollisionShapeProvider) {
            //Technically, the supporting block that vanilla calculates and caches is not always the one
            // that cancels the downwards motion, but usually it is, and this is only for a quick, additional test.
            //TODO: This may lead to the movement attempt not creating any chunk load tickets.
            // Entities and pistons **probably** create these tickets elsewhere anyways. This probably also applies
            // to usages of ChunkAwareBlockCollisionSweeperVoxelShape and others
            VoxelShape voxelShape = supportingBlockCollisionShapeProvider.lithium$getCollisionShapeBelow(entityBoundingBox, movement);
            if (voxelShape != null) {
                return voxelShape;
            }
        }
        return getCollisionShapeBelowEntityFallback(world, entity, entityBoundingBox, movement);
    }

    @Nullable
    private static VoxelShape getCollisionShapeBelowEntityFallback(Level world, Entity entity, AABB entityBoundingBox, Vec3 movement) {
        int x = Mth.floor(entityBoundingBox.minX + (entityBoundingBox.maxX - entityBoundingBox.minX) / 2);
        int y = Mth.floor(entityBoundingBox.minY);
        int z = Mth.floor(entityBoundingBox.minZ + (entityBoundingBox.maxZ - entityBoundingBox.minZ) / 2);
        if (world.isOutsideBuildHeight(y)) {
            return null;
        }
        ChunkAccess chunk = world.getChunk(Pos.ChunkCoord.fromBlockCoord(x), Pos.ChunkCoord.fromBlockCoord(z), ChunkStatus.FULL, false);
        if (chunk != null) {
            LevelChunkSection cachedChunkSection = chunk.getSections()[Pos.SectionYIndex.fromBlockCoord(world, y)];
            VoxelShape blockCollisionShape = cachedChunkSection.getBlockState(x & 15, y & 15, z & 15).
                    getCollisionShape(world, new BlockPos(x, y, z), entity == null ? CollisionContext.empty() : CollisionContext.of(entity));
            return getOffsetShapeIfVanillaWouldIteratePos(entityBoundingBox, blockCollisionShape, x, y, z, movement);
        }
        return null;
    }

    /**
     * Ensure to not iterate the shape when an incorrect <1e-7 to 0 movement snap could occur.
     * When the movement is smaller than 1e-7, we must skip the shape if it isn't iterated in vanilla.
     */
    public static VoxelShape getOffsetShapeIfVanillaWouldIteratePos(AABB entityBoundingBox, VoxelShape voxelShape, int x, int y, int z, Vec3 movement) {
        if (movement.get(Direction.Axis.Y) <= -1.0E-7) {
            //The movement is large enough to get no clipping anyway
            return voxelShape.move(x, y, z);
        } else {
            //Ensure to only return the non-null offset shape when it collides the movement volume
            AABB movementVolume = entityBoundingBox.expandTowards(movement);
            return ChunkAwareBlockCollisionSweeper.getCollidedShape(movementVolume, Shapes.create(movementVolume), voxelShape, x, y, z);
        }
    }

    public static boolean addLastBlockCollisionIfRequired(boolean addLastBlockCollision, ChunkAwareBlockCollisionSweeperVoxelShape blockCollisionSweeper, List<VoxelShape> list) {
        if (addLastBlockCollision) {
            VoxelShape lastCollision = blockCollisionSweeper.getLastCollision();
            if (lastCollision != null) {
                list.add(lastCollision);
            }
        }
        return false;
    }

    //TODO this makes the collision code include fewer blocks -> possibly a <1e-7 to 0 clipping is skipped. Reported by Andrews54757
    public static AABB getSmallerBoxForSingleAxisMovement(Vec3 movement, AABB entityBoundingBox, double velY, double velX, double velZ) {
        double minX = entityBoundingBox.minX;
        double minY = entityBoundingBox.minY;
        double minZ = entityBoundingBox.minZ;
        double maxX = entityBoundingBox.maxX;
        double maxY = entityBoundingBox.maxY;
        double maxZ = entityBoundingBox.maxZ;

        if (velY > 0) {
            //Reduced collision volume optimization for entities that only move in one direction:
            // If the entity is already inside the collision surface, it will not collide with it
            // Thus the surface / block can be skipped -> Only check for collisions outside the entity
            minY = maxY;
            maxY += velY;
        } else if (velY < 0) {
            maxY = minY;
            minY += velY;
        } else if (velX > 0) {
            minX = maxX;
            maxX += velX;
        } else if (velX < 0) {
            maxX = minX;
            minX += velX;
        } else if (velZ > 0) {
            minZ = maxZ;
            maxZ += velZ;
        } else if (velZ < 0) {
            maxZ = minZ;
            minZ += velZ;
        } else {
            //Movement is 0 or NaN, fall back to what vanilla usually does in this case
            return entityBoundingBox.expandTowards(movement);
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static boolean addEntityCollisionsIfRequired(boolean getEntityCollisions, @Nullable Entity entity, Level world, List<VoxelShape> entityCollisions, AABB movementSpace) {
        if (getEntityCollisions) {
            appendEntityCollisions(entityCollisions, world, entity, movementSpace);
        }
        return false;
    }
    public static boolean addWorldBorderCollisionIfRequired(boolean getWorldBorderCollision, @Nullable Entity entity, ArrayList<VoxelShape> worldBorderCollisions, AABB movementSpace) {
        if (getWorldBorderCollision && entity != null) {
            appendWorldBorderCollision(worldBorderCollisions, entity, movementSpace);
        }
        return false;
    }

    public interface SupportingBlockCollisionShapeProvider {

        @Nullable VoxelShape lithium$getCollisionShapeBelow(AABB entityBoundingBox, Vec3 movement);
    }
}
