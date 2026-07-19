package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import shit.zen.ZenClient;
import shit.zen.event.impl.RenderEvent;

@Patch(LevelRenderer.class)
public class LevelRendererPatch {
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
        if (ZenClient.isReady()) {
            PoseStack poseStack = new PoseStack();
            poseStack.last().pose().set(frustumMatrix);
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
            ZenClient.getInstance().getEventBus().call(new RenderEvent(poseStack, partialTick));
        }
    }
}
