package net.caffeinemc.mods.lithium.mixin.entity.equipment_tracking;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.caffeinemc.mods.lithium.common.entity.EquipmentInfo;
import net.caffeinemc.mods.lithium.common.util.change_tracking.ChangePublisher;
import net.caffeinemc.mods.lithium.common.util.change_tracking.ChangeSubscriber;
import net.caffeinemc.mods.lithium.common.world.in_world_tracking.MaybeInLevelObject;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;

@Mixin(EntityEquipment.class)
public class EntityEquipmentMixin implements EquipmentInfo, ChangeSubscriber.CountChangeSubscriber<ItemStack>, MaybeInLevelObject {
    @Shadow
    @Final
    private EnumMap<EquipmentSlot, ItemStack> items;
    @Unique
    boolean shouldTickEnchantments = false;
    @Unique
    ItemStack recheckEnchantmentForStack = null;
    @Unique
    boolean hasUnsentEquipmentChanges = true;

    @Unique
    boolean inLevel = false;

    @Override
    public boolean lithium$isInLevel() {
        return this.inLevel;
    }

    @Override
    public void lithium$handleAddedToLevel(Level level) {
        this.inLevel = true;
        this.initializeData();

        MaybeInLevelObject.super.lithium$handleAddedToLevel(level);
    }

    @Override
    public void lithium$handleRemovedFromLevel(Level level) {
        this.inLevel = false;
        this.invalidateData();

        MaybeInLevelObject.super.lithium$handleRemovedFromLevel(level);
    }

    @Override
    public boolean lithium$shouldTickEnchantments() {
        if (!this.inLevel) {
            return true;
        }

        this.processScheduledEnchantmentCheck(null);
        return this.shouldTickEnchantments;
    }

    @Override
    public boolean lithium$hasUnsentEquipmentChanges() {
        if (!this.inLevel) {
            return true;
        }

        return this.hasUnsentEquipmentChanges;
    }

    @Override
    public void lithium$onEquipmentChangesSent() {
        if (!this.inLevel) {
            return;
        }

        this.hasUnsentEquipmentChanges = false;
    }

    @WrapMethod(
            method = "set(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"
    )
    private ItemStack updateOnSet(EquipmentSlot equipmentSlot, ItemStack newStack, Operation<ItemStack> original) {
        ItemStack oldStack = original.call(equipmentSlot, newStack);
        if (this.inLevel) {
            this.onEquipmentReplaced(oldStack, newStack);
        }
        return oldStack;
    }

    @Inject(
            method = "setAll(Lnet/minecraft/world/entity/EntityEquipment;)V", at = @At("HEAD")
    )
    private void updateBeforeSetAll(EntityEquipment entityEquipment, CallbackInfo ci) {
        if (this.inLevel) {
            this.invalidateData();
        }
    }


    @Inject(
            method = "setAll(Lnet/minecraft/world/entity/EntityEquipment;)V", at = @At("RETURN")
    )
    private void updateAfterSetAll(EntityEquipment entityEquipment, CallbackInfo ci) {
        if (this.inLevel) {
            this.initializeData();
        }
    }

    @Inject(
            method = "clear", at = @At("HEAD")
    )
    private void updateOnClear(CallbackInfo ci) {
        if (this.inLevel) {
            this.invalidateData();
        }
    }

    @Unique
    private void invalidateData() {
        this.shouldTickEnchantments = false;
        this.recheckEnchantmentForStack = null;
        this.hasUnsentEquipmentChanges = true;

        for (ItemStack oldStack : this.items.values()) {
            if (!oldStack.isEmpty()) {
                //noinspection unchecked
                ((ChangePublisher<ItemStack>) (Object) oldStack).lithium$unsubscribeWithData(this, 0);
            }
        }
    }

    @Unique
    private void initializeData() {
        this.shouldTickEnchantments = false;
        this.recheckEnchantmentForStack = null;
        this.hasUnsentEquipmentChanges = true;

        for (ItemStack newStack : this.items.values()) {
            if (!newStack.isEmpty()) {
                if (!this.shouldTickEnchantments) {
                    this.shouldTickEnchantments = stackHasTickableEnchantment(newStack);
                }

                if (!newStack.isEmpty()) {
                    //noinspection unchecked
                    ((ChangePublisher<ItemStack>) (Object) newStack).lithium$subscribe(this, 0);
                }
            }
        }
    }


    @Unique
    private void onEquipmentReplaced(ItemStack oldStack, ItemStack newStack) {
        if (!this.shouldTickEnchantments) {
            if (this.recheckEnchantmentForStack == oldStack) {
                this.recheckEnchantmentForStack = null;
            }
            this.shouldTickEnchantments = stackHasTickableEnchantment(newStack);
        }

        this.hasUnsentEquipmentChanges = true;

        if (!oldStack.isEmpty()) {
            //noinspection unchecked
            ((ChangePublisher<ItemStack>) (Object) oldStack).lithium$unsubscribeWithData(this, 0);
        }
        if (!newStack.isEmpty()) {
            //noinspection unchecked
            ((ChangePublisher<ItemStack>) (Object) newStack).lithium$subscribe(this, 0);
        }
    }

    @Unique
    private static boolean stackHasTickableEnchantment(ItemStack stack) {
        if (!stack.isEmpty()) {
            ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
            if (enchantments != null && !enchantments.isEmpty()) {
                for (Holder<Enchantment> enchantmentEntry : enchantments.keySet()) {
                    if (!enchantmentEntry.value().getEffects(EnchantmentEffectComponents.TICK).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    @Override
    public void lithium$notify(@Nullable ItemStack publisher, int zero) {
        this.hasUnsentEquipmentChanges = true;

        if (!this.shouldTickEnchantments) {
            this.processScheduledEnchantmentCheck(publisher);
            this.scheduleEnchantmentCheck(publisher);
        }
    }

    @Unique
    private void scheduleEnchantmentCheck(@Nullable ItemStack toCheck) {
        this.recheckEnchantmentForStack = toCheck;
    }

    @Unique
    private void processScheduledEnchantmentCheck(@Nullable ItemStack ignoredStack) {
        if (this.recheckEnchantmentForStack != null && this.recheckEnchantmentForStack != ignoredStack) {
            this.shouldTickEnchantments = stackHasTickableEnchantment(this.recheckEnchantmentForStack);
            this.recheckEnchantmentForStack = null;
        }
    }

    @Override
    public void lithium$notifyCount(ItemStack publisher, int zero, int newCount) {
        if (newCount == 0) {
            //noinspection unchecked
            ((ChangePublisher<ItemStack>) (Object) publisher).lithium$unsubscribeWithData(this, zero);
        }

        this.onEquipmentReplaced(publisher, ItemStack.EMPTY);
    }

    @Override
    public void lithium$forceUnsubscribe(ItemStack publisher, int zero) {
        throw new UnsupportedOperationException();
    }
}
