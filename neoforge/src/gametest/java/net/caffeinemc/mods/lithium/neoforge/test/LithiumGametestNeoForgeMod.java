package net.caffeinemc.mods.lithium.neoforge.test;

import net.minecraft.gametest.framework.StructureUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.nio.file.Paths;

@Mod(value = "lithium")
public class LithiumGametestNeoForgeMod {
    public static final String LITHIUM_GAMETEST_SNBT_PATH = System.getenv("LITHIUM_GAMETEST_RESOURCES");

    static {
        StructureUtils.testStructuresSourceDir = Paths.get(LITHIUM_GAMETEST_SNBT_PATH);
        StructureUtils.testStructuresTargetDir = Paths.get(LITHIUM_GAMETEST_SNBT_PATH);
    }

    public LithiumGametestNeoForgeMod(IEventBus bus, ModContainer modContainer) {
        MixinEnvironment.getCurrentEnvironment().audit();
    }
}