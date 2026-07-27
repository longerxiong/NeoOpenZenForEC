package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.RenderEvent;
import shit.zen.utils.render.IrisCompatibility;
import shit.zen.utils.render.WorldOverlayRenderer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

@Patch(LevelRenderer.class)
public class LevelRendererPatch extends ClientBase {
    private static final MethodHandle getFov;

    static {
        try {
            Method m = GameRenderer.class.getDeclaredMethod("getFov", Camera.class, float.class, boolean.class);
            m.setAccessible(true);
            getFov = MethodHandles.lookup().unreflect(m);
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve GameRenderer.getFov", e);
        }
    }

    // Snapshot the camera and matrices at HEAD, before the frame-graph pass runs.
    //
    // Under Iris (with Sodium) the frustumMatrix / projectionMatrix instances are
    // reused and mutated in place during renderLevel (shadow pass, reversed-Z
    // handling, ...). Reading them at TAIL — as the overlay path does — therefore
    // sees corrupted camera data and everything in the world is drawn in the wrong
    // place. Iris itself works around this by storing a *copy* of the projection at
    // renderLevel HEAD; we do the same for both matrices here so our overlays use
    // exactly the camera the world was rendered with.
    //
    // Guard against the shadow pass: Iris invokes renderLevel again with the shadow
    // camera/projection, and capturing those would overwrite the real ones.
    @Inject(
            method = "renderLevel",
            desc = "(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRenderLevelHead(
            LevelRenderer renderer,
            GraphicsResourceAllocator allocator,
            DeltaTracker deltaTracker,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            CallbackInfo callbackInfo) {
        if (!ZenClient.isReady() || IrisCompatibility.isRenderingShadowPass()) {
            return;
        }
        WorldOverlayRenderer.capture(camera, frustumMatrix, projectionMatrix);
    }

    @Inject(
            method = "renderLevel",
            desc = "(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            at = @At(At.Type.TAIL)
    )
    public static void onRenderLevel(
            LevelRenderer renderer,
            GraphicsResourceAllocator allocator,
            DeltaTracker deltaTracker,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            CallbackInfo callbackInfo) {
        if (!ZenClient.isReady() || IrisCompatibility.isRenderingShadowPass()) {
            return;
        }

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);

        PoseStack poseStack = new PoseStack();
        // CPU-side projection — completely bypasses Iris GPU state. The camera and
        // matrices were snapshotted at renderLevel HEAD (see onRenderLevelHead);
        // begin() only re-reads the arguments here as a fallback if that hook did
        // not run, so we never depend on the (possibly mutated) TAIL matrices.
        WorldOverlayRenderer.begin(camera, frustumMatrix, projectionMatrix);
        try {
            ZenClient.getInstance().getEventBus().call(new RenderEvent(poseStack, partialTick));
        } finally {
            WorldOverlayRenderer.end();
        }
    }
}
