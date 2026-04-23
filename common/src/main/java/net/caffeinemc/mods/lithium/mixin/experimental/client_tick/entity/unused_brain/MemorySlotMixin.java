package net.caffeinemc.mods.lithium.mixin.experimental.client_tick.entity.unused_brain;

import net.caffeinemc.mods.lithium.common.client.SharedFields;
import net.minecraft.world.entity.ai.memory.MemorySlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MemorySlot.class)
public class MemorySlotMixin<T> {

    @Unique
    private static final String CRASH_MESSAGE = "Dummy client side brain memory slot cannot be modified! This is an optimization introduced by lithium. " +
            "Since minecraft clients do not execute mob AI logic, allocating complete brains is unnecessary. " +
            "This crash is thrown when a mod tries to write memories to client side brains anyway. If this really is " +
            "intended behavior, mod authors can check https://github.com/CaffeineMC/lithium/wiki/Disabling-Lithium's-Mixins-using-your-mod's-fabric.mod.json-or-neoforge.mods.toml " +
            "and disable the setting mixin.experimental.client_tick.entity.unused_brain while users can edit their " +
            "configuration file https://github.com/CaffeineMC/lithium/blob/develop/lithium-mixin-config.md to disable the same setting.";

    @Inject(
            method = "set(Ljava/lang/Object;J)V", at = @At("HEAD")
    )
    private void throwIfDummyMemorySlot(T value, long timeToLive, CallbackInfo ci) {
        if ((Object) this == SharedFields.DUMMY_SLOT) {
            throwOnModifyDummyMemorySlot();
        }
    }

    @Unique
    private static void throwOnModifyDummyMemorySlot() {
        throw new UnsupportedOperationException(
                CRASH_MESSAGE
        );
    }
}
