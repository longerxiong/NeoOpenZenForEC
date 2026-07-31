package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;

/**
 * Limits simultaneous native mesh-buffer growth on memory-heavy resource packs.
 */
@Patch(className = "net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder")
public final class SodiumChunkBuilderPatch {
    private static final int MAX_CHUNK_BUILD_THREADS = 3;

    private SodiumChunkBuilderPatch() {
    }

    @Inject(method = "getThreadCount", desc = "()I", at = @At(At.Type.HEAD))
    public static void onGetThreadCount(CallbackInfo callbackInfo) {
        callbackInfo.result = MAX_CHUNK_BUILD_THREADS;
        callbackInfo.cancel();
    }
}
