package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;

/**
 * Bounds server-driven particle load before particle instances are allocated.
 */
@Patch(ParticleEngine.class)
public final class ParticleEnginePatch {
    private static final int MAX_ACTIVE_PARTICLES = 2048;
    private static final int MAX_NEW_PARTICLES_PER_TICK = 128;

    private static ParticleEngine currentEngine;
    private static int activeAtTickStart;
    private static int createdThisTick;

    @Inject(method = "tick", desc = "()V", at = @At(At.Type.HEAD))
    public static void onTick(ParticleEngine engine, CallbackInfo callbackInfo) {
        currentEngine = engine;
        createdThisTick = 0;
        try {
            activeAtTickStart = Integer.parseInt(engine.countParticles());
        } catch (NumberFormatException ignored) {
            activeAtTickStart = 0;
        }
    }

    @Inject(
            method = "createParticle",
            desc = "(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At(At.Type.HEAD)
    )
    public static void onCreateParticle(ParticleEngine engine, ParticleOptions options,
                                        double x, double y, double z,
                                        double velocityX, double velocityY, double velocityZ,
                                        CallbackInfo callbackInfo) {
        if (engine != currentEngine) {
            currentEngine = engine;
            activeAtTickStart = 0;
            createdThisTick = 0;
        }

        if (createdThisTick >= MAX_NEW_PARTICLES_PER_TICK
                || activeAtTickStart + createdThisTick >= MAX_ACTIVE_PARTICLES) {
            // A null result is the vanilla signal that no particle was created.
            callbackInfo.result = null;
            callbackInfo.cancel();
            return;
        }
        createdThisTick++;
    }
}
