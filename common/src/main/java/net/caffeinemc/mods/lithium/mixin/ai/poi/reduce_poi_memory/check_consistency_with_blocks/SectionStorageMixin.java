package net.caffeinemc.mods.lithium.mixin.ai.poi.reduce_poi_memory.check_consistency_with_blocks;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.caffeinemc.mods.lithium.common.world.interests.PoiCheckConsistency;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;
import java.util.function.Function;

@Mixin(SectionStorage.class)
public abstract class SectionStorageMixin<R> implements PoiCheckConsistency<R> {
    @Shadow
    @Final
    private Long2ObjectMap<Optional<R>> storage;

    @Shadow
    @Final
    private Function<Runnable, R> factory;

    @Shadow
    protected abstract void setDirty(long l);

    @Override
    public R lithium$createSectionAndInsert(long l) {
        R object = this.factory.apply(() -> this.setDirty(l));
        this.storage.put(l, Optional.of(object));
        return object;
    }
}
