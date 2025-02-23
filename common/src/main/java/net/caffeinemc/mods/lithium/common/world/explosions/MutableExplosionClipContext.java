package net.caffeinemc.mods.lithium.common.world.explosions;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class MutableExplosionClipContext {
    public final Level level;
    public final Vec3 to;
    public Vec3 from = null;

    public MutableExplosionClipContext(Level level, Vec3 to) {
        this.level = level;
        this.to = to;
    }
}
