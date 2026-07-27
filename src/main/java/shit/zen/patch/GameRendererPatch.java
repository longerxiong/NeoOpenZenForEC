package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Overwrite;
import asm.patchify.annotation.Patch;
import asm.patchify.annotation.WrapInvoke;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import shit.zen.ZenClient;
import shit.zen.utils.rotation.RotationHandler;
import shit.zen.asm.Invocation;
import shit.zen.event.impl.GlRenderEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.modules.impl.render.AspectRatio;
import shit.zen.modules.impl.render.FullBright;
import shit.zen.modules.impl.render.NoHurtCam;
import shit.zen.render.Renderer;
import shit.zen.utils.misc.ReflectionUtil;
import shit.zen.utils.render.WorldOverlayRenderer;

@Patch(GameRenderer.class)
public class GameRendererPatch {
    @Overwrite(method = "getNightVisionScale", desc = "(Lnet/minecraft/world/entity/LivingEntity;F)F")
    public static float overwriteGetNightVisionScale(LivingEntity entity, float partial) {
        if (FullBright.INSTANCE != null && FullBright.INSTANCE.isEnabled()) {
            return FullBright.INSTANCE.brightnessSetting.getValue().floatValue() / 100.0f;
        }
        return entity.hasEffect(MobEffects.NIGHT_VISION) ? 1.0f : 0.0f;
    }

    // 3D world overlays: injected in LevelRendererPatch TAIL (reliable).
    // Do not use AFTER_INVOKE of LevelRenderer.renderLevel here — owner/name
    // remapping often finds zero sites.

    @Inject(
            method = "render",
            desc = "(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(value = At.Type.AFTER_INVOKE, method = "net/minecraft/client/gui/Gui/render", desc = "(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V")
    )
    public static void onRender(GameRenderer gameRenderer, DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callbackInfo) {
        GuiRenderState guiRenderState = (GuiRenderState)ReflectionUtil.getStaticField(
                gameRenderer, "guiRenderState", "net/minecraft/client/renderer/GameRenderer");
        GuiGraphics graphics = new GuiGraphics(gameRenderer.getMinecraft(), guiRenderState);
        PoseStack poseStack = new PoseStack();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        if (ZenClient.isReady()) {
            Renderer.render(graphics, drawContext -> {
                ZenClient.getInstance().getEventBus().call(new Render2DEvent(poseStack, graphics, partialTick));
                poseStack.pushPose();
                try {
                    ZenClient.getInstance().getEventBus().call(new GlRenderEvent(graphics, poseStack, drawContext));
                } finally {
                    poseStack.popPose();
                }
                // Draw all world-overlay geometry (ESP boxes, etc.) submitted this
                // frame — from either the 3D world pass or the 2D events above —
                // through the GUI canvas, which is Iris-safe.
                WorldOverlayRenderer.flush(drawContext);
            });
        }
    }

    @WrapInvoke(
            method = "getProjectionMatrix",
            desc = "(F)Lorg/joml/Matrix4f;",
            target = "org/joml/Matrix4f/perspective",
            targetDesc = "(FFFF)Lorg/joml/Matrix4f;"
    )
    public static Matrix4f onGetProjectionMatrix(GameRenderer gameRenderer, float fov, Invocation<GameRenderer, Matrix4f> original) throws Exception {
        if (!ZenClient.isReady() || AspectRatio.INSTANCE == null || !AspectRatio.INSTANCE.isEnabled()) {
            return original.call();
        }
        return new Matrix4f().perspective(
                fov * (float) (Math.PI / 180.0),
                AspectRatio.INSTANCE.ratioSetting.getValue().floatValue(),
                0.05f,
                gameRenderer.getDepthFar());
    }

    // ===== Keep the FOV steady while the rotation system is turning the head =====
    // Silent rotation makes vanilla drop sprint for a few ticks, because the movement
    // input no longer counts as "forward" relative to the yaw being sent. Losing sprint
    // removes the movement-speed attribute bonus, and the FOV shrinks with it — so the
    // view pumps in and out on every aim. While a rotation is running and the player is
    // still moving, hold the FOV at the value it had just before the rotation started,
    // which keeps that sprint break invisible. This is purely visual: sprint state,
    // movement and packets are untouched.
    private static float fovBeforeRotation = Float.NaN;

    @Inject(
            method = "getFov",
            desc = "(Lnet/minecraft/client/Camera;FZ)F",
            at = @At(At.Type.TAIL)
    )
    public static void onGetFov(GameRenderer gameRenderer, Camera camera, float partialTick,
                                boolean useFovSetting, CallbackInfo callbackInfo) {
        // useFovSetting == false is the fixed FOV used for the held item — never touch it.
        if (!useFovSetting || !(callbackInfo.result instanceof Number number)) {
            return;
        }
        float current = number.floatValue();
        LocalPlayer player = gameRenderer.getMinecraft().player;
        if (!ZenClient.isReady() || player == null
                || !RotationHandler.isRotating || !isMovingHorizontally(player)) {
            // Nothing to hide: follow the real FOV so we resume from the right value.
            fovBeforeRotation = current;
            return;
        }
        if (Float.isNaN(fovBeforeRotation)) {
            fovBeforeRotation = current;
        }
        callbackInfo.result = fovBeforeRotation;
    }

    private static boolean isMovingHorizontally(LocalPlayer player) {
        double dx = player.getX() - player.xOld;
        double dz = player.getZ() - player.zOld;
        return dx * dx + dz * dz > 1.0E-4;
    }

    @Inject(method = "bobHurt", desc = "(Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At(At.Type.HEAD))
    public static void onBobHurt(GameRenderer gameRenderer, PoseStack poseStack, float partial, CallbackInfo callbackInfo) {
        if (ZenClient.isReady() && NoHurtCam.INSTANCE != null && NoHurtCam.INSTANCE.isEnabled()) {
            callbackInfo.cancel();
        }
    }
}
