package net.caffeinemc.mods.lithium.mixin.shapes.lazy_shape_context;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityCollisionContext.class)
public abstract class EntityCollisionContextMixin {
    @Mutable
    @Shadow
    @Final
    private ItemStack heldItem;

    @Shadow
    @Final
    @Nullable
    private Entity entity;

    @Mutable
    @Shadow
    @Final
    private boolean descending;
    @Unique
    private boolean isDescendingNeedsInitialization;

    /**
     * Mixin the instanceof to always return false to avoid the expensive inventory access.
     * No need to use Opcodes.INSTANCEOF or similar.
     */
    @ModifyConstant(
            method = "<init>(Lnet/minecraft/world/entity/Entity;ZZ)V",
            constant = @Constant(classValue = LivingEntity.class, ordinal = 0)
    )
    private static boolean redirectInstanceOf(Object obj, Class<?> clazz) {
        return false;
    }


    @Inject(
            method = "<init>(Lnet/minecraft/world/entity/Entity;ZZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/shapes/EntityCollisionContext;<init>(ZZDLnet/minecraft/world/item/ItemStack;ZLnet/minecraft/world/entity/Entity;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void initFields(Entity entity, boolean standOnEveryFluid, boolean isPlacement, CallbackInfo ci) {
        this.heldItem = null;
    }

    @Inject(
            method = "isHoldingItem(Lnet/minecraft/world/item/Item;)Z",
            at = @At("HEAD")
    )
    public void isHolding(Item item, CallbackInfoReturnable<Boolean> cir) {
        this.initHeldItem();
    }

    @Intrinsic
    public ItemStack getHeldItem() {
        return this.heldItem;
    }

    @SuppressWarnings({"UnresolvedMixinReference", "MixinAnnotationTarget"})
    @Inject(
            method = "getHeldItem", remap = false,
            at = @At("HEAD")
    )
    private void initHeldItem(CallbackInfoReturnable<ItemStack> callbackInfoReturnable) {
        this.initHeldItem();
    }

    @Unique
    private void initHeldItem() {
        if (this.heldItem == null) {
            this.heldItem = this.entity instanceof LivingEntity ? ((LivingEntity) this.entity).getMainHandItem() : ItemStack.EMPTY;
        }
    }

    @WrapOperation(
            method = "<init>(Lnet/minecraft/world/entity/Entity;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isDescending()Z")
    )
    private static boolean skipIsDescending(Entity instance, Operation<Boolean> original) {
        return false;
    }

    @Inject(
            method = "<init>(Lnet/minecraft/world/entity/Entity;ZZ)V",
            at = @At("RETURN")
    )
    private void initVars(CallbackInfo ci) {
        this.isDescendingNeedsInitialization = true;
    }

    @Inject(
            method = "isDescending", at = @At("HEAD")
    )
    private void initIsDescending(CallbackInfoReturnable<Boolean> cir) {
        if (this.isDescendingNeedsInitialization) {
            this.isDescendingNeedsInitialization = false;
            this.descending = this.entity != null && this.entity.isDescending();
        }
    }
}
