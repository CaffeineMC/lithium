package net.caffeinemc.mods.lithium.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LithiumMod {
    private static final Logger LOGGER = LoggerFactory.getLogger("Lithium");

    private static String MOD_VERSION;

    private static final boolean DEBUG = false;

    public static void onInitialization(String version) {
        MOD_VERSION = version;
    }

    public static Logger logger() {
        if (LOGGER == null) {
            throw new IllegalStateException("Logger not yet available");
        }

        return LOGGER;
    }

    public static String getVersion() {
        if (MOD_VERSION == null) {
            throw new NullPointerException("Mod version hasn't been populated yet");
        }

        return MOD_VERSION;
    }
}
