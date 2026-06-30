package net.caffeinemc.mods.lithium.mixin.experimental.entity.block_caching.block_support;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.lithium.common.entity.LithiumEntityCollisions;
import net.caffeinemc.mods.lithium.common.tracking.VicinityCache;
import net.caffeinemc.mods.lithium.common.tracking.VicinityCacheProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(Entity.class)
public abstract class EntityMixin implements VicinityCacheProvider, LithiumEntityCollisions.SupportingBlockCollisionShapeProvider {
    @Shadow
    public abstract Level level();

    @Shadow
    protected abstract double getGravity();

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Shadow
    public Optional<BlockPos> mainSupportingBlockPos;

    @Inject(
            method = "checkSupportingBlock(ZLnet/minecraft/world/phys/Vec3;)V", cancellable = true,
            at = @At(
                    value = "INVOKE", shift = At.Shift.BEFORE,
                    target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"
            )
    )
    private void cancelIfSkippable(boolean onGround, Vec3 movement, CallbackInfo ci) {
        if (movement == null || (movement.x == 0 && movement.z == 0)) {
            //noinspection ConstantConditions
            VicinityCache bc = this.getUpdatedVicinityCache((Entity) (Object) this);
            if (bc.canSkipSupportingBlockSearch()) {
                ci.cancel();
            }
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Inject(
            method = "checkSupportingBlock(ZLnet/minecraft/world/phys/Vec3;)V",
            at = @At(
                    value = "INVOKE_ASSIGN", ordinal = 0, shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/world/level/Level;findSupportingBlock(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/Optional;"
            )
    )
    private void cacheSupportingBlockSearch(CallbackInfo ci, @Local Optional<BlockPos> pos) {
        VicinityCache bc = this.lithium$getVicinityCache();
        if (bc.isTracking()) {
            bc.setCanSkipSupportingBlockSearch(true);
            if (pos.isPresent() && this.getGravity() > 0D) {
                bc.cacheSupportingBlockState(this.level().getBlockState(pos.get()));
            }
        }
    }

    @Inject(
            method = "checkSupportingBlock(ZLnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/world/level/Level;findSupportingBlock(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/Optional;")
    )
    private void uncacheSupportingBlockSearch(CallbackInfo ci) {
        VicinityCache bc = this.lithium$getVicinityCache();
        if (bc.isTracking()) {
            bc.setCanSkipSupportingBlockSearch(false);
        }
    }

    @Inject(
            method = "checkSupportingBlock(ZLnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Optional;empty()Ljava/util/Optional;", remap = false)
    )
    private void uncacheSupportingBlockSearch1(boolean onGround, Vec3 movement, CallbackInfo ci) {
        VicinityCache bc = this.lithium$getVicinityCache();
        if (bc.isTracking()) {
            bc.setCanSkipSupportingBlockSearch(false);
        }
    }

    @Override
    public @Nullable VoxelShape lithium$getCollisionShapeBelow(AABB entityBoundingBox, Vec3 movement) {
        VicinityCache bc = this.getUpdatedVicinityCache((Entity) (Object) this);
        if (bc.isTracking()) {
            BlockState cachedSupportingBlock = bc.getCachedSupportingBlock();
            if (cachedSupportingBlock != null && this.mainSupportingBlockPos.isPresent()) {
                BlockPos blockPos = this.mainSupportingBlockPos.get();
                VoxelShape blockCollisionShape = cachedSupportingBlock.getCollisionShape(this.level(), blockPos, CollisionContext.of((Entity) (Object) this));
                return LithiumEntityCollisions.getOffsetShapeIfVanillaWouldIteratePos(entityBoundingBox, blockCollisionShape, blockPos.getX(), blockPos.getY(), blockPos.getZ(), movement);
            }
        }
        return null;
    }
}
