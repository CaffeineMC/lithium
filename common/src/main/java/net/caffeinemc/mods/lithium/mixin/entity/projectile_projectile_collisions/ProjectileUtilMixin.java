package net.caffeinemc.mods.lithium.mixin.entity.projectile_projectile_collisions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.lithium.common.entity.projectile.ProjectileCanHitEntityPredicate;
import net.caffeinemc.mods.lithium.common.entity.projectile.ProjectileEntityClassGroups;
import net.caffeinemc.mods.lithium.common.world.WorldHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {

    @WrapOperation(
            method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;")
    )
    private static List<Entity> getEntitiesForCollision(Level level, @Nullable Entity searchingEntity, AABB box, Predicate<? super Entity> entityFilter, Operation<List<Entity>> original) {
        if (searchingEntity != null && entityFilter instanceof ProjectileCanHitEntityPredicate && ProjectileEntityClassGroups.OPTIMIZED_PROJECTILES.contains(searchingEntity)) {
            EntitySectionStorage<Entity> cache = WorldHelper.getEntityCacheOrNull(level);
            if (cache != null) {
                return WorldHelper.getEntitiesOfEntityGroupPlusDragonPieces(level, cache, searchingEntity, ProjectileEntityClassGroups.CAN_MAYBE_BE_HIT_BY_OPTIMIZED_PROJECTILE, box, entityFilter);
            }
        }
        return original.call(level, searchingEntity, box, entityFilter);
    }
}
