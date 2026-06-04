package net.caffeinemc.mods.lithium.mixin.profiler;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(Profiler.class)
public class ProfilerMixin {

    @Shadow
    @Final
    private static ThreadLocal<ProfilerFiller> ACTIVE;

    //Typically there are at most two threads using the profiler (in singleplayer: Server Thread and Client Thread)
    @Unique
    private static final AtomicReference<Thread> THREAD_1 = new AtomicReference<>();
    @Unique
    private static ProfilerFiller profiler1;
    @Unique
    private static final AtomicReference<Thread> THREAD_2 = new AtomicReference<>();
    @Unique
    private static ProfilerFiller profiler2;

    @Unique
    private static final AtomicInteger ACTIVE_PROFILER_COUNT = new AtomicInteger(0);

    //The thread local lookup is about 2% of server runtime when this optimization isn't applied.
    //This optimization reduces this to around 1% when there is an active profiler, and even less when there isn't
    @WrapMethod(
            method = "get"
    )
    private static ProfilerFiller lithium$getProfiler(Operation<ProfilerFiller> original) {
        if (ACTIVE_PROFILER_COUNT.get() == 0) {
            return InactiveProfiler.INSTANCE;
        }
        return getProfilerFromFields(original);
    }

    @Unique
    private static ProfilerFiller getProfilerFromFields(Operation<ProfilerFiller> original) {
        Thread thread = Thread.currentThread();
        if (THREAD_1.get() == thread) {
            return profiler1;
        } else if (THREAD_2.get() == thread) {
            return profiler2;
        }
        return original.call();
    }

    @Inject(
            method = "use",
            at = @At("RETURN")
    )
    private static void placeProfilerInStaticFields(ProfilerFiller filler, CallbackInfoReturnable<Profiler.Scope> cir) {
        Thread thread = Thread.currentThread();
        ProfilerFiller activeProfiler = ACTIVE.get();
        if (THREAD_1.compareAndSet(null, thread)) {
            profiler1 = activeProfiler;
        } else if (THREAD_2.compareAndSet(null, thread)) {
            profiler2 = activeProfiler;
        }
        if (activeProfiler != null && !(activeProfiler instanceof InactiveProfiler)) {
            ACTIVE_PROFILER_COUNT.incrementAndGet();
        }
    }

    @Inject(
            method = "stopUsing",
            at = @At("RETURN")
    )
    private static void removeProfilerFromStaticFields(CallbackInfo ci) {
        Thread thread = Thread.currentThread();
        ProfilerFiller activeProfiler = null;
        if (THREAD_1.get() == thread) {
            activeProfiler = profiler1;
            profiler1 = null;
            THREAD_1.set(null);
        } else if (THREAD_2.get() == thread) {
            activeProfiler = profiler2;
            profiler2 = null;
            THREAD_2.set(null);
        }
        if (activeProfiler != null && !(activeProfiler instanceof InactiveProfiler)) {
            ACTIVE_PROFILER_COUNT.decrementAndGet();
        }
    }
}
