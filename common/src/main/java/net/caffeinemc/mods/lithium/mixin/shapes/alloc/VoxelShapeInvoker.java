package net.caffeinemc.mods.lithium.mixin.shapes.alloc;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(VoxelShape.class)
public interface VoxelShapeInvoker {

    @Invoker("findIndex")
    int callFindIndex(final Direction.Axis axis, final double coord);

}
