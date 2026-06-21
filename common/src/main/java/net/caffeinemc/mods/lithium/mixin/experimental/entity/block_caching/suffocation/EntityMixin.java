package net.caffeinemc.mods.lithium.mixin.experimental.entity.block_caching.suffocation;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.lithium.common.shapes.offset_operations.LithiumOffsetShapes;
import net.caffeinemc.mods.lithium.common.tracking.VicinityCache;
import net.caffeinemc.mods.lithium.common.tracking.VicinityCacheProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin implements VicinityCacheProvider {

    @Shadow
    public Level level;

    @Shadow
    private EntityDimensions dimensions;

    protected EntityMixin(EntityDimensions dimensions) {
        this.dimensions = dimensions;
    }

    /**
     * @author 2No2Name
     * @reason Avoid stream code, use optimized chunk section iteration order
     */
    @Inject(
            method = "isInWall", cancellable = true,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;betweenClosedStream(Lnet/minecraft/world/phys/AABB;)Ljava/util/stream/Stream;",
                    shift = At.Shift.BEFORE
            )
    )
    public void isInsideWall(CallbackInfoReturnable<Boolean> cir, @Local(name = "eyeBb") AABB eyeBb) {
        // [VanillaCopy]
        int minX = Mth.floor(eyeBb.minX);
        int minY = Mth.floor(eyeBb.minY);
        int minZ = Mth.floor(eyeBb.minZ);
        int maxX = Mth.floor(eyeBb.maxX);
        int maxY = Mth.floor(eyeBb.maxY);
        int maxZ = Mth.floor(eyeBb.maxZ);

        VicinityCache bc = this.getUpdatedVicinityCache((Entity) (Object) this);

        byte cachedSuffocation = bc.getIsSuffocating();
        if (cachedSuffocation == (byte) 0) {
            cir.setReturnValue(false);
            return;
        } else if (cachedSuffocation == (byte) 1) {
            cir.setReturnValue(true);
            return;
        }

        Level world = this.level;
        //skip getting blocks when the entity is outside the world height
        //also avoids infinite loop with entities below y = Integer.MIN_VALUE (some modded servers do that)
        if (world.getMinY() > maxY || world.getMaxY() < minY) {
            bc.setCachedIsSuffocating(false);
            cir.setReturnValue(false);
            return;
        }

        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

        boolean shouldCache = true;

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    blockPos.set(x, y, z);
                    BlockState blockState = world.getBlockState(blockPos);
                    if (!blockState.isAir() && blockState.isSuffocating(this.level, blockPos)) {
                        //We must never cache suffocation with shulker boxes, as they can change suffocation behavior without block state changes and the block listening system therefore does not detect these changes.
                        if (shouldCache && blockState.is(BlockTags.SHULKER_BOXES)) {
                            shouldCache = false;
                        }

                        if (LithiumOffsetShapes.joinIsNotEmpty(blockState.getCollisionShape(this.level, blockPos), blockPos.getX(), blockPos.getY(), blockPos.getZ(), eyeBb, null, BooleanOp.AND)) {
                            if (shouldCache) {
                                bc.setCachedIsSuffocating(true);
                            }
                            cir.setReturnValue(true);
                            return;
                        }
                    }
                }
            }
        }
        if (shouldCache) {
            bc.setCachedIsSuffocating(false);
        }
        cir.setReturnValue(false);
    }
}
