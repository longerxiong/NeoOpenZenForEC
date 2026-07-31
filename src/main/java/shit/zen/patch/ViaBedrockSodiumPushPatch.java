package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ViaBedrockUtility copies an entire cosmetic model through LWJGL's small
 * thread-local MemoryStack. Detailed models can exceed it in a single push.
 */
@Patch(className = "org.oryxel.viabedrockutility.renderer.SodiumVertexPushBackend")
public final class ViaBedrockSodiumPushPatch {
    private static final Logger LOGGER = LogManager.getLogger(ViaBedrockSodiumPushPatch.class);
    private static final int VERTEX_STRIDE = 36;
    private static final int MAX_VERTICES_PER_PUSH = 128;
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    private static volatile Class<?> resolvedClass;
    private static volatile Method pushMethod;

    @Inject(method = "va", desc = "(Ljava/lang/Object;JI)V", at = @At(At.Type.HEAD))
    public static void onPush(Object backend, Object writer, long address, int vertexCount,
                              CallbackInfo callbackInfo) {
        if (vertexCount <= MAX_VERTICES_PER_PUSH) {
            return;
        }

        try {
            Method method = resolvePushMethod(backend.getClass());
            int remaining = vertexCount;
            long chunkAddress = address;
            while (remaining > 0) {
                int chunkSize = Math.min(remaining, MAX_VERTICES_PER_PUSH);
                method.invoke(backend, writer, chunkAddress, chunkSize);
                chunkAddress += (long) chunkSize * VERTEX_STRIDE;
                remaining -= chunkSize;
            }
        } catch (Throwable throwable) {
            Throwable cause = throwable instanceof InvocationTargetException invocation
                    && invocation.getCause() != null ? invocation.getCause() : throwable;
            if (REPORTED_FAILURE.compareAndSet(false, true)) {
                LOGGER.warn("Failed to split an oversized ViaBedrock cosmetic batch; "
                        + "skipping the batch to prevent a render-thread crash", cause);
            }
        }

        // Either all chunks were rendered or the unsafe cosmetic batch was skipped.
        callbackInfo.cancel();
    }

    private static Method resolvePushMethod(Class<?> backendClass) throws NoSuchMethodException {
        Method cached = pushMethod;
        if (cached != null && resolvedClass == backendClass) {
            return cached;
        }
        synchronized (ViaBedrockSodiumPushPatch.class) {
            if (pushMethod == null || resolvedClass != backendClass) {
                Method method = backendClass.getMethod("va", Object.class, long.class, int.class);
                method.setAccessible(true);
                resolvedClass = backendClass;
                pushMethod = method;
            }
            return pushMethod;
        }
    }
}
