package net.caffeinemc.mods.lithium.mixin.world.block_entity_ticking.sleeping.sculk_sensor_shrieker;

import net.caffeinemc.mods.lithium.common.block.entity.sleeping_sculk.GameEventListenerWithCallback;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VibrationSystem.Listener.class)
public abstract class VibrationSystemListenerMixin implements GameEventListenerWithCallback {
    @Unique
    private Runnable listener;

    @Override
    public void lithium$setGameEventCallback(Runnable listener) {
        this.listener = listener;
    }

    @Inject(method = "scheduleVibration(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$Data;Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/gameevent/GameEvent$Context;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V", at = @At(value = "HEAD"))
    private void onCurrentVibrationUpdate(ServerLevel level, VibrationSystem.Data data, Holder<GameEvent> event, GameEvent.Context context, Vec3 origin, Vec3 dest, CallbackInfo ci) {
        if (this.listener != null) {
            this.listener.run();
        }
    }
}
