package net.caffeinemc.mods.lithium.mixin.chunk.serialization;

import net.caffeinemc.mods.lithium.common.world.chunk.CompactingPackedIntegerArray;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PaletteResize;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Extends {@link SimpleBitStorage} with a special compaction method defined in {@link CompactingPackedIntegerArray}.
 */
@Mixin(SimpleBitStorage.class)
public abstract class SimpleBitStorageMixin implements CompactingPackedIntegerArray {
    @Shadow
    @Final
    private int size;

    @Shadow
    @Final
    private int bits;

    @Shadow
    @Final
    private long mask;

    @Shadow
    @Final
    private int valuesPerLong;

    @Shadow
    @Final
    private long[] data;

    @Override
    public <T> void lithium$compact(Palette<T> srcPalette, Palette<T> dstPalette, short[] out) {
        if (this.size >= Short.MAX_VALUE) {
            throw new IllegalStateException("Array too large");
        }

        if (this.size != out.length) {
            throw new IllegalStateException("Array size mismatch");
        }

        PaletteResize<T> noResizeExpected = PaletteResize.noResizeExpected();

        short[] mappings = new short[(int) (this.mask + 1)];

        int idx = 0;

        for (long word : this.data) {
            long bits = word;

            for (int elementIdx = 0; elementIdx < this.valuesPerLong; ++elementIdx) {
                int value = (int) (bits & this.mask);
                int remappedId = mappings[value];

                if (remappedId == 0) {
                    remappedId = dstPalette.idFor(srcPalette.valueFor(value), noResizeExpected) + 1;
                    mappings[value] = (short) remappedId;
                }

                out[idx] = (short) (remappedId - 1);
                bits >>= this.bits;

                ++idx;

                if (idx >= this.size) {
                    return;
                }
            }
        }
    }
}
