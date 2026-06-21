package net.caffeinemc.mods.lithium.mixin.alloc.shapes.slice;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.SliceShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SliceShape.class)
public interface SliceShapeAccessor {

    @Accessor("axis")
    Direction.Axis getAxis();

}
