package core;

import net.caffeinemc.mods.lithium.common.util.change_tracking.ChangePublisher;
import net.caffeinemc.mods.lithium.mixin.LithiumMixinPlugin;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import util.TestBootstrap;

public class CoreTest extends TestBootstrap {

    @Test
    void testMixinApply() {
        //noinspection ConstantValue
        Assertions.assertEquals(ChangePublisher.class.isAssignableFrom(ItemStack.class), !LithiumMixinPlugin.DISABLE_ALL_MIXINS);
    }
}
