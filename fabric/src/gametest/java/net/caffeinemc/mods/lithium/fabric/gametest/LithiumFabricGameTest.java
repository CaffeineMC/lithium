package net.caffeinemc.mods.lithium.fabric.gametest;


import net.fabricmc.api.ModInitializer;
import net.minecraft.gametest.framework.StructureUtils;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.nio.file.Paths;

public class LithiumFabricGameTest implements ModInitializer {

    public static final String LITHIUM_GAMETEST_SNBT_PATH = System.getenv("LITHIUM_GAMETEST_RESOURCES");

    @Override
    public void onInitialize() {
        StructureUtils.testStructuresSourceDir = Paths.get(LITHIUM_GAMETEST_SNBT_PATH);
        StructureUtils.testStructuresTargetDir = Paths.get(LITHIUM_GAMETEST_SNBT_PATH);

        MixinEnvironment.getCurrentEnvironment().audit();
    }
}

