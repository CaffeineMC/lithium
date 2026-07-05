package util;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;

public abstract class TestBootstrap {

    private static final Object BOOTSTRAP_LOCK = new Object();
    private static boolean bootstrapped;

    @BeforeAll
    static void beforeAll() {
        synchronized (BOOTSTRAP_LOCK) {
            if (!bootstrapped) {
                // Minecraft bootstrap initializes global registries and other static state.
                // The tests in this suite rely on that state, so we initialize it once for the
                // whole JVM instead of repeating it in each test class.
                SharedConstants.tryDetectVersion();
                Bootstrap.bootStrap();
                bootstrapped = true;
            }
        }
    }
}

