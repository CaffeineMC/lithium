package net.caffeinemc.mods.lithium.common.block;

import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public abstract class TrackedBlockStatePredicate implements Predicate<BlockState> {
    public static final AtomicBoolean FULLY_INITIALIZED;

    static {
        FULLY_INITIALIZED = new AtomicBoolean(false);
        if (!BlockStateFlags.ENABLED) { //classload the BlockStateFlags class which initializes the content of ALL_FLAGS
            System.out.println("Lithium Cached BlockState Flags are disabled!");
        }
    }

    private final int index;
    private final boolean fallbackResult;

    public TrackedBlockStatePredicate(int index, boolean fallbackResult) {
        if (FULLY_INITIALIZED.get()) {
            throw new IllegalStateException("Lithium Cached BlockState Flags: Cannot register more flags after assuming to be fully initialized.");
        }
        this.index = index;
        this.fallbackResult = fallbackResult;
    }

    public int getIndex() {
        return this.index;
    }

    public boolean getFallbackResult() {
        return this.fallbackResult;
    }
}
