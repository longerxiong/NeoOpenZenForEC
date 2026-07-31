package shit.zen.utils.misc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Releases world-scoped allocations before Sodium starts building the next world.
 */
public final class MemoryPressureGuard {
    private static final Logger LOGGER = LogManager.getLogger(MemoryPressureGuard.class);

    private static final long MIN_USED_HEAP_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final double GC_HEAP_RATIO = 0.60D;
    private static final long GC_COOLDOWN_MS = 2_000L;

    private static final AtomicBoolean PARTICLE_LIMITS_ATTEMPTED = new AtomicBoolean();
    private static volatile Object bedrockParticleManager;
    private static volatile Method bedrockParticleClear;
    private static volatile long lastGcAt;

    private MemoryPressureGuard() {
    }

    /**
     * BEParticle is an optional, independent engine, so vanilla ParticleEngine limits do not
     * affect it. Configure its own built-in limits without adding a hard dependency on the mod.
     */
    public static void configureOptionalParticleLimits() {
        if (!PARTICLE_LIMITS_ATTEMPTED.compareAndSet(false, true)) {
            return;
        }

        try {
            ClassLoader loader = Minecraft.class.getClassLoader();
            Class<?> managerClass = Class.forName(
                    "net.easecation.beparticle.ParticleManager", true, loader);
            Field instanceField = managerClass.getField("INSTANCE");
            Object manager = instanceField.get(null);

            managerClass.getMethod("setGlobalMaxParticles", int.class).invoke(manager, 2_048);
            managerClass.getMethod("setSoftMaxParticles", int.class).invoke(manager, 1_024);
            managerClass.getMethod("setHardMaxParticles", int.class).invoke(manager, 2_048);
            managerClass.getMethod("setMaxRenderDistance", float.class).invoke(manager, 40.0F);
            managerClass.getMethod("setParticleTickLodEnabled", boolean.class)
                    .invoke(manager, true);
            managerClass.getMethod("setParticleTickLodNearDistance", float.class)
                    .invoke(manager, 20.0F);
            managerClass.getMethod("setParticleTickLodFarDistance", float.class)
                    .invoke(manager, 36.0F);

            bedrockParticleManager = manager;
            bedrockParticleClear = managerClass.getMethod("clearEmitters");
            LOGGER.info("Applied BEParticle safety limits (soft=1024, hard=2048, distance=40)");
        } catch (ClassNotFoundException ignored) {
            // Optional mod is not installed.
        } catch (Throwable throwable) {
            LOGGER.warn("Unable to configure optional BEParticle limits", throwable);
        }
    }

    /**
     * Called after Minecraft has detached the previous level or attached the next one.
     */
    public static void afterLevelChange(Minecraft minecraft) {
        clearOptionalParticles();

        Runtime runtime = Runtime.getRuntime();
        long usedBefore = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        long now = System.currentTimeMillis();
        if (usedBefore < MIN_USED_HEAP_BYTES
                || usedBefore < (long) (max * GC_HEAP_RATIO)
                || now - lastGcAt < GC_COOLDOWN_MS) {
            return;
        }

        lastGcAt = now;
        System.gc();
        long usedAfter = runtime.totalMemory() - runtime.freeMemory();
        LOGGER.info("World-change memory trim: {} MiB -> {} MiB (max {} MiB)",
                usedBefore / 1024L / 1024L,
                usedAfter / 1024L / 1024L,
                max / 1024L / 1024L);
    }

    private static void clearOptionalParticles() {
        Object manager = bedrockParticleManager;
        Method clear = bedrockParticleClear;
        if (manager == null || clear == null) {
            return;
        }
        try {
            clear.invoke(manager);
        } catch (Throwable throwable) {
            LOGGER.debug("Unable to clear BEParticle emitters during level change", throwable);
        }
    }
}
