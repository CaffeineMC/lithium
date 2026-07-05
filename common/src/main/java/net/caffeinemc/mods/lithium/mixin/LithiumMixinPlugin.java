package net.caffeinemc.mods.lithium.mixin;

import net.caffeinemc.mods.lithium.common.config.LithiumConfig;
import net.caffeinemc.mods.lithium.common.config.Option;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.File;
import java.util.List;
import java.util.Set;

public class LithiumMixinPlugin implements IMixinConfigPlugin {
    private static final String[] MIXIN_PACKAGE_ROOTS = {"net.caffeinemc.mods.lithium.mixin.", "net.caffeinemc.mods.lithium.fabric.mixin.", "net.caffeinemc.mods.lithium.neoforge.mixin."};

    private static final String DISABLE_ALL_MIXINS_PROPERTY = "lithium.test.disable_all_mixins";
    public static final boolean DISABLE_ALL_MIXINS = Boolean.parseBoolean(System.getProperty(DISABLE_ALL_MIXINS_PROPERTY));

    private final Logger logger = LogManager.getLogger("Lithium");

    private static LithiumConfig CONFIG;
    public static boolean DEBUG = false;

    @Override
    public void onLoad(String mixinPackage) {
        if (DISABLE_ALL_MIXINS) {
            this.logger.warn("All Lithium Mixins are disabled via VM argument lithium.test.disable_all_mixins=true");
            return;
        }

        if (CONFIG != null) {
            return;
        }

        try {
            CONFIG = LithiumConfig.load(new File("./config/lithium.properties"));
        } catch (Exception e) {
            throw new RuntimeException("Could not load configuration file for Lithium", e);
        }
        String experimentalInfo = CONFIG.isOptionEnabled("mixin.experimental") ? " Experimental features are enabled! " : "";
        DEBUG = CONFIG.isOptionEnabled("mixin.debug");
        String debugInfo = DEBUG ? "Lithium debug statements are enabled!" : "";
        this.logger.info("Loaded configuration file for Lithium: {} options available, {} override(s) found.{}{}",
                CONFIG.getOptionCount(), CONFIG.getOptionOverrideCount(), experimentalInfo, debugInfo);

    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (DISABLE_ALL_MIXINS) {
            return false;
        }
        if (DEBUG) {
            this.logger.info("Checking mixin '{}' for target '{}'", mixinClassName, targetClassName);
        }
        String mixin = null;
        for (String root : MIXIN_PACKAGE_ROOTS) {
            if (mixinClassName.startsWith(root)) {
                mixin = mixinClassName.substring(root.length());
                break;
            }
        }
        if (mixin == null) {
            this.logger.error("Expected mixin '{}' to start with any of these package roots '{}', treating as foreign and " +
                    "disabling!", mixinClassName, MIXIN_PACKAGE_ROOTS);
            return false;
        }

        Option option = CONFIG.getEffectiveOptionForMixin(mixin);

        if (option == null) {
            this.logger.error("No rules matched mixin '{}', treating as foreign and disabling!", mixin);

            return false;
        }

        if (option.isOverridden()) {
            String source = "[unknown]";

            if (option.isUserDefined()) {
                source = "user configuration";
            } else if (option.isModDefined()) {
                source = "mods [" + String.join(", ", option.getDefiningMods()) + "]";
            }

            if (option.isEnabled()) {
                this.logger.info("Force-enabling mixin '{}' as rule '{}' (added by {}) enables it", mixin,
                        option.getName(), source);
            } else {
                this.logger.info("Force-disabling mixin '{}' as rule '{}' (added by {}) disables it and children", mixin,
                        option.getName(), source);
            }
        }

        boolean enabled = option.isEnabled();
        if (DEBUG) {
            if (!enabled) {
                this.logger.info("Disabling mixin '{}' due to rule '{}'", mixin, option.getName());
            } else {
                this.logger.info("Enabling mixin '{}' due to rule '{}'", mixin, option.getName());
            }
        }
        return enabled;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
