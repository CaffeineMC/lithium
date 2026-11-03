package net.caffeinemc.mods.lithium.forge;

import net.caffeinemc.mods.lithium.common.services.PlatformRuntimeInformation;
import net.minecraftforge.fml.loading.FMLConfig;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;

import java.nio.file.Path;

public class ForgeRuntimeInformation implements PlatformRuntimeInformation {
    @Override
    public boolean isDevelopmentEnvironment() {
        // Forge keeps this static where NeoForge routes it through FMLLoader.getCurrent().
        return !FMLLoader.isProduction();
    }

    @Override
    public Path getGameDirectory() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean platformHasEarlyLoadingScreen() {
        return FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL);
    }

    @Override
    public boolean isModInLoadingList(String modId) {
        // LoadingModList is a fully static interface on Forge 26.x.
        return LoadingModList.getModFileById(modId) != null;
    }
}
