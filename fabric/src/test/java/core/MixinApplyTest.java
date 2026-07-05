package core;

import net.caffeinemc.mods.lithium.mixin.LithiumMixinPlugin;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import util.TestBootstrap;
import util.TestUtils;

public class MixinApplyTest extends TestBootstrap {

    @Test
    void testMixinApply() {
        Assertions.assertEquals(TestUtils.IS_MIXIN_LOADED, !LithiumMixinPlugin.DISABLE_ALL_MIXINS);
    }
}
