package net.caffeinemc.mods.lithium.mixin.entity.collisions.intersection;

import net.caffeinemc.mods.lithium.common.entity.LithiumEntityCollisions;
import net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeperBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Optional;

/**
 * Replaces collision testing methods with jumps to our own (faster) entity collision testing code.
 */
@Mixin(Level.class)
public abstract class LevelMixin implements LevelAccessor {

    /**
     * Checks whether the area is empty from blocks, hard entities and the world border.
     * Only access relevant entity classes, use more efficient block access
     * @author 2No2Name
     */
    @Override
    public boolean noCollision(@Nullable Entity entity, @NonNull AABB box) {
        boolean ret = !LithiumEntityCollisions.doesBoxCollideWithBlocks((Level) (Object) this, entity, box);

        // If no blocks were collided with, try to check for entity collisions if we can read entities
        if (ret) {
            //needs to include world border collision
            ret = !LithiumEntityCollisions.doesBoxCollideWithHardEntities(this, entity, box);
        }

        if (ret && entity != null) {
            ret = !LithiumEntityCollisions.doesBoxCollideWithWorldBorder(this, entity, box);
        }

        return ret;
    }

    /**
     * Use ChunkAwareBlockCollisionSweeper for supporting block search
     * Order is already irrelevant since vanilla compares block positions as a tiebreaker.
     * @author jcw780
     */
    @Override
    public @NotNull Optional<BlockPos> findSupportingBlock(@NotNull Entity entity, @NotNull AABB aABB) {
        BlockPos blockPos = null;
        double d = Double.MAX_VALUE;
        ChunkAwareBlockCollisionSweeperBlockPos blockCollisions = new ChunkAwareBlockCollisionSweeperBlockPos((Level) (Object) this, entity, aABB);

        while (blockCollisions.hasNext()) {
            BlockPos blockPos2 = blockCollisions.next();
            double e = blockPos2.distToCenterSqr(entity.position());
            if (e < d || e == d && (blockPos == null || blockPos.compareTo(blockPos2) < 0)) {
                blockPos = blockPos2.immutable();
                d = e;
            }
        }

        return Optional.ofNullable(blockPos);
    }
}